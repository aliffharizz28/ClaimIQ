package com.claimiq.triage;

import com.claimiq.claim.*;
import com.claimiq.security.ClaimAccess;
import com.claimiq.identity.UserRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class TriageService {
    private static final String RULE_VERSION = "motor-v3";
    private static final int RECENT_CLAIM_WINDOW_DAYS = 30;
    private static final int MIN_NARRATIVE_LENGTH = 40;
    private final ClaimRepository claims;
    private final EvidenceRepository evidence;
    private final TriageResultRepository results;
    private final TriageFindingRepository findings;
    private final ClaimAccess access;
    private final UserRepository users;

    public TriageService(ClaimRepository claims, EvidenceRepository evidence, TriageResultRepository results,
                         TriageFindingRepository findings, ClaimAccess access, UserRepository users) {
        this.claims = claims; this.evidence = evidence; this.results = results; this.findings = findings; this.access = access; this.users = users;
    }
    public record FindingView(FindingType type, String explanation, Long relatedClaimId) { }
    public record ResultView(Long id, Long claimId, Priority priority, Instant analyzedAt, String ruleVersion, List<FindingView> findings) { }
    private record Finding(FindingType type, String explanation, Claim related) { }

    /** Caller holds the claimant lock. Recompute the affected date window without recursive propagation. */
    @Transactional
    public void reconcile(Claim claim) {
        analyze(claim);
        if (claim.getIncidentDate() == null) return;
        var date = claim.getIncidentDate();
        claims.findByClaimantIdAndIncidentDateBetweenAndIdNotOrderByIdAsc(claim.getClaimant().getId(),
                date.minusDays(RECENT_CLAIM_WINDOW_DAYS), date.plusDays(RECENT_CLAIM_WINDOW_DAYS), claim.getId())
                .forEach(this::analyze);
    }

    /** Called inside the claim transaction after submission or additional evidence. */
    @Transactional
    public void analyze(Claim claim) {
        List<Finding> detected = new ArrayList<>();
        var supplied = evidence.findByClaimIdOrderByIdAsc(claim.getId());
        if (supplied.stream().noneMatch(e -> e.getType() == EvidenceType.DAMAGE_PHOTO && e.getVerificationStatus() != VerificationStatus.REJECTED))
            detected.add(new Finding(FindingType.EVIDENCE_GAP, "No damage photo is recorded for review.", null));
        if (supplied.stream().noneMatch(e -> e.getType() == EvidenceType.REPAIR_QUOTATION && e.getVerificationStatus() != VerificationStatus.REJECTED))
            detected.add(new Finding(FindingType.EVIDENCE_GAP, "No repair quotation is recorded for review.", null));
        if (claim.getIncidentDate() != null) {
            String registration = Claim.normalizeRegistration(claim.getVehicleRegistration());
            String narrative = normalizeNarrative(claim.getDescription());
            var date = claim.getIncidentDate();
            if (!registration.isBlank()) claims.findByClaimantIdAndNormalizedRegistrationAndIncidentDateBetweenAndIdNotOrderByIdAsc(
                    claim.getClaimant().getId(), registration, date.minusDays(RECENT_CLAIM_WINDOW_DAYS), date.plusDays(RECENT_CLAIM_WINDOW_DAYS), claim.getId())
                .forEach(other -> {
                    long days = Math.abs(ChronoUnit.DAYS.between(date, other.getIncidentDate()));
                    if (days == 0)
                        detected.add(new Finding(FindingType.POSSIBLE_DUPLICATE,
                            "Same claimant, incident date and normalized vehicle registration as claim " + other.getId() + "; review for a possible duplicate.", other));
                    else
                        detected.add(new Finding(FindingType.RECENT_VEHICLE_CLAIM,
                            "Same claimant and vehicle as claim " + other.getId() + ", with incident dates " + days
                                + (days == 1 ? " day" : " days") + " apart; review whether the claims are related. Separate incidents are possible.", other));
                });
            if (narrative.length() >= MIN_NARRATIVE_LENGTH) claims.findByClaimantIdAndIncidentDateBetweenAndIdNotOrderByIdAsc(
                    claim.getClaimant().getId(), date.minusDays(RECENT_CLAIM_WINDOW_DAYS), date.plusDays(RECENT_CLAIM_WINDOW_DAYS), claim.getId())
                .forEach(other -> {
                    boolean exactVehicleDate = !registration.isBlank() && registration.equals(other.getNormalizedRegistration()) && date.equals(other.getIncidentDate());
                    if (narrative.equals(normalizeNarrative(other.getDescription())) && !exactVehicleDate)
                        detected.add(new Finding(FindingType.REPEATED_NARRATIVE,
                            "The same claimant used the same incident description on claim " + other.getId()
                                + " within 30 days. Review the context; repeated text alone does not establish a duplicate or fraud.", other));
                });
        }
        Priority priority = detected.stream().anyMatch(f -> f.type() == FindingType.POSSIBLE_DUPLICATE) ? Priority.HIGH
                : detected.isEmpty() ? Priority.NORMAL : Priority.MEDIUM;
        TriageResult result = results.save(new TriageResult(claim, priority, Instant.now(), RULE_VERSION));
        for (Finding f : detected) findings.save(new TriageFinding(result, f.type(), f.explanation(), f.related()));
    }
    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('CLAIMANT','OFFICER')")
    public ResultView latest(Authentication auth, Long claimId) {
        Claim claim = claims.findById(claimId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Claim not found"));
        if (!access.canRead(auth, claim)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Claim not found");
        TriageResult result = results.findFirstByClaimIdOrderByIdDesc(claimId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No triage result available"));
        boolean officer = auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_OFFICER"));
        var items = findings.findByTriageResultIdOrderByIdAsc(result.getId()).stream()
                .filter(f -> officer || f.getType() == FindingType.EVIDENCE_GAP)
                .map(f -> new FindingView(f.getType(), f.getExplanation(), f.getRelatedClaim() == null ? null : f.getRelatedClaim().getId())).toList();
        Priority visiblePriority = officer ? result.getPriority() : items.isEmpty() ? Priority.NORMAL : Priority.MEDIUM;
        return new ResultView(result.getId(), claimId, visiblePriority, result.getAnalyzedAt(), result.getRuleVersion(), items);
    }
    @Transactional
    @PreAuthorize("hasRole('OFFICER')")
    public ResultView refresh(Authentication auth, Long claimId) {
        Claim claim = claims.findById(claimId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Claim not found"));
        users.lockById(claim.getClaimant().getId()).orElseThrow();
        reconcile(claim);
        return latest(auth, claimId);
    }
    private String normalizeNarrative(String description) {
        return description == null ? "" : description.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }
}
