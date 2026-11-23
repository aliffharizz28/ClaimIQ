package com.claimiq.triage;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/claims/{claimId}/triage")
public class TriageController {
    private final TriageService service;
    public TriageController(TriageService service) { this.service = service; }
    @GetMapping public TriageService.ResultView latest(Authentication auth, @PathVariable Long claimId) { return service.latest(auth, claimId); }
    @PostMapping public TriageService.ResultView refresh(Authentication auth, @PathVariable Long claimId) { return service.refresh(auth, claimId); }
}
