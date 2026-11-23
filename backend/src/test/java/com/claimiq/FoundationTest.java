package com.claimiq;

import com.claimiq.claim.Claim;
import com.claimiq.identity.Role;
import com.claimiq.identity.User;
import com.claimiq.identity.UserRepository;
import com.claimiq.security.ClaimAccess;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.web.servlet.MockMvc;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("demo")
class FoundationTest {
    @Autowired UserRepository users;
    @Autowired ClaimAccess access;
    @Autowired MockMvc mvc;
    @Autowired PasswordEncoder encoder;
    @Autowired EntityManager entityManager;

    @Test void demoUsersHaveEncodedPasswordsAndDistinctRoles() {
        var claimant = users.findByEmailIgnoreCase("claimant@example.test").orElseThrow();
        var officer = users.findByEmailIgnoreCase("officer@example.test").orElseThrow();
        assertThat(claimant.getRole()).isEqualTo(Role.CLAIMANT);
        assertThat(officer.getRole()).isEqualTo(Role.OFFICER);
        assertThat(claimant.getPasswordHash()).startsWith("$2").isNotEqualTo("demo-password");
        assertThat(encoder.matches("demo-password", claimant.getPasswordHash())).isTrue();
    }
    @Test void accessPolicyDistinguishesOwnershipAndOfficer() {
        User owner = new User("Owner", "owner@example.test", "hash", Role.CLAIMANT);
        Claim claim = new Claim(owner, LocalDate.of(2026, 1, 1), "ABC123", "Incident", new BigDecimal("100.00"));
        assertThat(access.canRead(auth("owner@example.test", "ROLE_CLAIMANT"), claim)).isTrue();
        assertThat(access.canRead(auth("other@example.test", "ROLE_CLAIMANT"), claim)).isFalse();
        assertThat(access.canRead(auth("officer@example.test", "ROLE_OFFICER"), claim)).isTrue();
        assertThat(access.canRead(null, claim)).isFalse();
    }
    @Test void unknownRoutesAreClosedByDefault() throws Exception {
        mvc.perform(get("/unopened")).andExpect(status().isUnauthorized());
        mvc.perform(get("/unopened").with(httpBasic("claimant@example.test", "demo-password")))
                .andExpect(status().isForbidden());
    }
    @Test @Transactional void claimPersistsWithOwnerAndProvisionalExposure() {
        User owner = users.findByEmailIgnoreCase("claimant@example.test").orElseThrow();
        Claim claim = new Claim(owner, LocalDate.of(2026, 1, 1), "ABC123", "Incident", new BigDecimal("100.00"));
        entityManager.persist(claim);
        entityManager.flush();
        Long id = claim.getId();
        entityManager.clear();
        Claim stored = entityManager.find(Claim.class, id);
        assertThat(stored.getClaimant().getEmail()).isEqualTo("claimant@example.test");
        assertThat(stored.getProvisionalExposure()).isEqualByComparingTo("100.00");
    }
    private UsernamePasswordAuthenticationToken auth(String name, String role) {
        return new UsernamePasswordAuthenticationToken(name, "unused", List.of(new SimpleGrantedAuthority(role)));
    }
}
