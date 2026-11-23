package com.claimiq.security;

import com.claimiq.claim.Claim;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/** Policy helper for future service or method-level authorization. */
@Component("claimAccess")
public class ClaimAccess {
    public boolean canRead(Authentication authentication, Claim claim) {
        if (authentication == null || !authentication.isAuthenticated() || claim == null) return false;
        boolean officer = authentication.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_OFFICER"));
        boolean claimant = authentication.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_CLAIMANT"));
        return officer || (claimant && claim.getClaimant().getEmail().equalsIgnoreCase(authentication.getName()));
    }
}
