package com.claimiq.security;

import com.claimiq.identity.Role;
import com.claimiq.identity.User;
import com.claimiq.identity.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
@Profile("demo")
public class DemoUsers {
    @Bean CommandLineRunner seedDemoUsers(UserRepository users, PasswordEncoder encoder) {
        return args -> {
            seed(users, encoder, "Demo Claimant", "claimant@example.test", Role.CLAIMANT);
            seed(users, encoder, "Demo Officer", "officer@example.test", Role.OFFICER);
        };
    }
    private void seed(UserRepository users, PasswordEncoder encoder, String name, String email, Role role) {
        if (users.findByEmailIgnoreCase(email).isEmpty()) {
            users.save(new User(name, email, encoder.encode("demo-password"), role));
        }
    }
}
