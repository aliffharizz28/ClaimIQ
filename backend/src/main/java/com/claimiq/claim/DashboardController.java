package com.claimiq.claim;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DashboardController {
    private final ClaimService service;
    public DashboardController(ClaimService service) { this.service = service; }
    @GetMapping("/api/v1/dashboard/summary") public ClaimService.Dashboard summary() { return service.dashboard(); }
}
