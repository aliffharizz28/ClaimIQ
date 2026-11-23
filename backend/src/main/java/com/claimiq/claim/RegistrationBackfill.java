package com.claimiq.claim;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Populate the added index column for existing local prototype data. */
@Component
public class RegistrationBackfill implements ApplicationRunner {
    private final ClaimRepository claims;
    public RegistrationBackfill(ClaimRepository claims) { this.claims = claims; }
    @Override @Transactional public void run(ApplicationArguments args) {
        claims.findByNormalizedRegistrationIsNull().forEach(Claim::normalizeRegistration);
    }
}
