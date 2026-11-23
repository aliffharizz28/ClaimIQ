package com.claimiq.claim;

import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/claims")
public class ClaimController {
    private final ClaimService service;
    public ClaimController(ClaimService service) { this.service = service; }
    @PostMapping public ResponseEntity<ClaimDtos.ClaimView> submit(Authentication auth, @Valid @RequestBody ClaimDtos.Submit input) {
        var claim = service.submit(auth, input);
        return ResponseEntity.created(URI.create("/api/v1/claims/" + claim.id())).body(claim);
    }
    public record ClaimPage(List<ClaimDtos.ClaimView> content, int number, int totalPages, long totalElements) { }
    @GetMapping public ClaimPage list(Authentication auth, @RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="20") int size) {
        var result = service.list(auth, page, size);
        return new ClaimPage(result.getContent(), result.getNumber(), result.getTotalPages(), result.getTotalElements());
    }
    @GetMapping("/{id}") public ClaimDtos.ClaimView get(Authentication auth, @PathVariable Long id) { return service.get(auth, id); }
    @PostMapping("/{id}/assign") public ClaimDtos.ClaimView assign(Authentication auth, @PathVariable Long id, @Valid @RequestBody ClaimDtos.Assign input) { return service.assign(auth, id, input); }
    @PostMapping("/{id}/request-info") public ClaimDtos.ClaimView requestInfo(Authentication auth, @PathVariable Long id, @Valid @RequestBody ClaimDtos.Action input) { return service.requestInfo(auth, id, input); }
    @PostMapping("/{id}/additional-info") public ClaimDtos.ClaimView additionalInfo(Authentication auth, @PathVariable Long id, @Valid @RequestBody ClaimDtos.AdditionalInfo input) { return service.additionalInfo(auth, id, input); }
    @PostMapping("/{id}/decision") public ClaimDtos.ClaimView decide(Authentication auth, @PathVariable Long id, @Valid @RequestBody ClaimDtos.Decision input) { return service.decide(auth, id, input); }
    @PostMapping("/{id}/evidence/{evidenceId}/review") public ClaimDtos.ClaimView reviewEvidence(Authentication auth, @PathVariable Long id, @PathVariable Long evidenceId, @Valid @RequestBody ClaimDtos.EvidenceReview input) { return service.reviewEvidence(auth, id, evidenceId, input); }
}
