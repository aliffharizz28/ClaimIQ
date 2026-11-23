package com.claimiq.claim;

import com.claimiq.identity.Role;
import com.claimiq.identity.User;
import com.claimiq.identity.UserRepository;
import com.claimiq.security.ClaimAccess;
import com.claimiq.triage.TriageService;
import java.util.List;
import java.util.Map;
import java.util.EnumMap;
import java.math.BigDecimal;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

@Service
public class ClaimService {
    private final ClaimRepository claims;
    private final EvidenceRepository evidence;
    private final ClaimActivityRepository activities;
    private final UserRepository users;
    private final ClaimAccess access;
    private final TriageService triage;

    public ClaimService(ClaimRepository claims, EvidenceRepository evidence, ClaimActivityRepository activities,
                        UserRepository users, ClaimAccess access, TriageService triage) {
        this.claims = claims; this.evidence = evidence; this.activities = activities; this.users = users; this.access = access; this.triage = triage;
    }
    @Transactional
    @PreAuthorize("hasRole('CLAIMANT')")
    public ClaimDtos.ClaimView submit(Authentication auth, ClaimDtos.Submit input) {
        User actor = actor(auth);
        users.lockById(actor.getId()).orElseThrow();
        Claim claim = claims.save(new Claim(actor, input.incidentDate(), input.vehicleRegistration().trim(), input.description().trim(), input.claimedAmount()));
        addEvidence(claim, input.evidence());
        log(claim, actor, "SUBMITTED", null);
        triage.reconcile(claim);
        claims.flush();
        return view(claim);
    }
    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('CLAIMANT','OFFICER')")
    public Page<ClaimDtos.ClaimView> list(Authentication auth, int page, int size) {
        if (page < 0 || size < 1 || size > 100) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "page must be non-negative and size must be between 1 and 100");
        User actor = actor(auth);
        var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"));
        return (actor.getRole() == Role.OFFICER ? claims.findAll(pageable) : claims.findByClaimantId(actor.getId(), pageable)).map(this::view);
    }
    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('CLAIMANT','OFFICER')")
    public ClaimDtos.ClaimView get(Authentication auth, Long id) { return view(visible(auth, id)); }

    public record Dashboard(long totalClaims, Map<ClaimStatus, Long> countsByStatus, BigDecimal outstandingProvisionalExposure) { }
    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('OFFICER')")
    public Dashboard dashboard() {
        Map<ClaimStatus, Long> counts = new EnumMap<>(ClaimStatus.class);
        for (ClaimStatus status : ClaimStatus.values()) counts.put(status, 0L);
        BigDecimal outstanding = BigDecimal.ZERO;
        long total = 0;
        for (var group : claims.summarize()) {
            counts.put(group.getStatus(), group.getCount());
            total += group.getCount();
            if (group.getStatus() != ClaimStatus.APPROVED && group.getStatus() != ClaimStatus.REJECTED)
                outstanding = outstanding.add(group.getExposure());
        }
        return new Dashboard(total, counts, outstanding);
    }

    @Transactional
    @PreAuthorize("hasRole('OFFICER')")
    public ClaimDtos.ClaimView assign(Authentication auth, Long id, ClaimDtos.Assign input) {
        Claim claim = visible(auth, id); checkVersion(claim, input.version());
        requireStatus(claim, ClaimStatus.SUBMITTED, ClaimStatus.UNDER_REVIEW);
        User officer = users.findById(input.officerId()).filter(u -> u.getRole() == Role.OFFICER)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Officer not found"));
        claim.assign(officer);
        log(claim, actor(auth), "ASSIGNED", "Assigned to " + officer.getEmail() + (input.note() == null || input.note().isBlank() ? "" : ": " + input.note().trim()));
        return changed(claim);
    }
    @Transactional
    @PreAuthorize("hasRole('OFFICER')")
    public ClaimDtos.ClaimView requestInfo(Authentication auth, Long id, ClaimDtos.Action input) {
        Claim claim = visible(auth, id); checkVersion(claim, input.version()); requireStatus(claim, ClaimStatus.UNDER_REVIEW);
        claim.requestInfo(); log(claim, actor(auth), "INFO_REQUESTED", input.note().trim()); return changed(claim);
    }
    @Transactional
    @PreAuthorize("hasRole('CLAIMANT')")
    public ClaimDtos.ClaimView additionalInfo(Authentication auth, Long id, ClaimDtos.AdditionalInfo input) {
        Claim claim = visible(auth, id);
        users.lockById(claim.getClaimant().getId()).orElseThrow();
        checkVersion(claim, input.version()); requireStatus(claim, ClaimStatus.MORE_INFO_REQUIRED);
        addEvidence(claim, input.evidence()); claim.provideInfo(); log(claim, actor(auth), "INFO_PROVIDED", input.note().trim()); triage.reconcile(claim); return changed(claim);
    }
    @Transactional
    @PreAuthorize("hasRole('OFFICER')")
    public ClaimDtos.ClaimView decide(Authentication auth, Long id, ClaimDtos.Decision input) {
        Claim claim = visible(auth, id); checkVersion(claim, input.version()); requireStatus(claim, ClaimStatus.UNDER_REVIEW);
        if (claim.getAssignedOfficer() == null || !claim.getAssignedOfficer().getId().equals(actor(auth).getId()))
            throw new AccessDeniedException("Only the assigned officer may decide this claim");
        if (input.decision() != ClaimStatus.APPROVED && input.decision() != ClaimStatus.REJECTED)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Decision must be APPROVED or REJECTED");
        claim.decide(input.decision()); log(claim, actor(auth), "DECISION_" + input.decision(), input.note().trim()); return changed(claim);
    }
    @Transactional
    @PreAuthorize("hasRole('OFFICER')")
    public ClaimDtos.ClaimView reviewEvidence(Authentication auth, Long id, Long evidenceId, ClaimDtos.EvidenceReview input) {
        Claim claim = visible(auth, id);
        users.lockById(claim.getClaimant().getId()).orElseThrow();
        checkVersion(claim, input.version());
        requireStatus(claim, ClaimStatus.UNDER_REVIEW);
        if (claim.getAssignedOfficer() == null || !claim.getAssignedOfficer().getId().equals(actor(auth).getId()))
            throw new AccessDeniedException("Only the assigned officer may review evidence");
        if (input.status() == VerificationStatus.UNVERIFIED)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Evidence must be VERIFIED or REJECTED");
        Evidence item = evidence.findById(evidenceId).filter(e -> e.getClaim().getId().equals(id))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Evidence not found"));
        item.setVerificationStatus(input.status());
        claim.evidenceChanged();
        log(claim, actor(auth), "EVIDENCE_" + input.status(), "Evidence #" + evidenceId + ": " + input.note().trim());
        triage.reconcile(claim);
        return changed(claim);
    }
    private User actor(Authentication auth) {
        return users.findByEmailIgnoreCase(auth.getName()).orElseThrow(() -> new AccessDeniedException("Unknown user"));
    }
    private Claim visible(Authentication auth, Long id) {
        Claim claim = claims.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Claim not found"));
        if (!access.canRead(auth, claim)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Claim not found");
        return claim;
    }
    private void checkVersion(Claim claim, long version) {
        if (claim.getVersion() != version) throw new ResponseStatusException(HttpStatus.CONFLICT, "Claim changed; reload and retry");
    }
    private void requireStatus(Claim claim, ClaimStatus... allowed) {
        for (ClaimStatus status : allowed) if (claim.getStatus() == status) return;
        throw new ResponseStatusException(HttpStatus.CONFLICT, "Action is not allowed in the current status");
    }
    private void addEvidence(Claim claim, List<ClaimDtos.EvidenceInput> items) {
        if (items != null) for (var item : items) evidence.save(new Evidence(claim, item.type(), item.reference().trim()));
    }
    private void log(Claim claim, User actor, String action, String note) { activities.save(new ClaimActivity(claim, actor, action, note)); }
    private ClaimDtos.ClaimView changed(Claim claim) { claims.flush(); return view(claim); }
    private ClaimDtos.ClaimView view(Claim claim) {
        var evidenceViews = evidence.findByClaimIdOrderByIdAsc(claim.getId()).stream()
                .map(e -> new ClaimDtos.EvidenceView(e.getId(), e.getType(), e.getReference(), e.getVerificationStatus())).toList();
        var activityViews = activities.findByClaimIdOrderByIdAsc(claim.getId()).stream()
                .map(a -> new ClaimDtos.ActivityView(a.getId(), a.getActor().getEmail(), a.getAction(), a.getNote(), a.getOccurredAt())).toList();
        return new ClaimDtos.ClaimView(claim.getId(), claim.getClaimant().getId(), claim.getAssignedOfficer() == null ? null : claim.getAssignedOfficer().getId(),
                claim.getIncidentDate(), claim.getVehicleRegistration(), claim.getDescription(), claim.getClaimedAmount(),
                claim.getProvisionalExposure(), claim.getStatus(), claim.getCreatedAt(), claim.getVersion(), evidenceViews, activityViews);
    }
}
