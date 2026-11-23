package com.claimiq;

import com.claimiq.identity.Role;
import com.claimiq.identity.User;
import com.claimiq.identity.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("demo")
class TriageConcurrencyTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder encoder;

    @Test void simultaneousMatchingSubmissionsReconcileBothClaims() throws Exception {
        String email = "concurrent@example.test", password = "demo-password";
        users.findByEmailIgnoreCase(email).orElseGet(() -> users.save(new User("Concurrent", email, encoder.encode(password), Role.CLAIMANT)));
        String body = """
          {"incidentDate":"2024-04-01","vehicleRegistration":"CON-123","description":"Incident",
           "claimedAmount":100,"evidence":[{"type":"DAMAGE_PHOTO","reference":"p"},{"type":"REPAIR_QUOTATION","reference":"q"}]}
          """;
        var gate = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> { gate.await(); return submit(email, password, body); });
            var second = executor.submit(() -> { gate.await(); return submit(email, password, body); });
            gate.countDown();
            long a = first.get(30, TimeUnit.SECONDS), b = second.get(30, TimeUnit.SECONDS);
            assertThat(a).isNotEqualTo(b);
            for (long id : new long[]{a, b}) {
                var result = mvc.perform(get("/api/v1/claims/" + id + "/triage").with(httpBasic("officer@example.test",password)))
                        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
                var triage = json.readTree(result);
                assertThat(triage.get("priority").asText()).isEqualTo("HIGH");
                assertThat(triage.get("findings").get(0).get("relatedClaimId").asLong()).isEqualTo(id == a ? b : a);
                var claimant = mvc.perform(get("/api/v1/claims/" + id + "/triage").with(httpBasic(email,password)))
                        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
                assertThat(json.readTree(claimant).get("findings").size()).isZero();
                assertThat(json.readTree(claimant).get("priority").asText()).isEqualTo("NORMAL");
            }
        }
    }
    private long submit(String email, String password, String body) throws Exception {
        var response = mvc.perform(post("/api/v1/claims").with(httpBasic(email,password)).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(response).get("id").asLong();
    }
}
