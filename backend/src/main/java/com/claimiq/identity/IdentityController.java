package com.claimiq.identity;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class IdentityController {
    private final UserRepository users;
    public IdentityController(UserRepository users) { this.users = users; }
    public record Identity(Long id, String name, String email, Role role) { }
    @GetMapping("/api/v1/me") public Identity me(Authentication auth) {
        User user = users.findByEmailIgnoreCase(auth.getName()).orElseThrow(() -> new AccessDeniedException("Unknown user"));
        return new Identity(user.getId(), user.getName(), user.getEmail(), user.getRole());
    }
}
