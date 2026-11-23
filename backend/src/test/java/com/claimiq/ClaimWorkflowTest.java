package com.claimiq;

import com.claimiq.identity.Role;
import com.claimiq.identity.User;
import com.claimiq.identity.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("demo")
class ClaimWorkflowTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder encoder;
    private static final String CLAIMANT = "claimant@example.test", OFFICER = "officer@example.test", PASSWORD = "demo-password";
    private static final String SUBMISSION = """
        {"incidentDate":"2025-01-01","vehicleRegistration":"ABC123","description":"Rear collision",
         "claimedAmount":2500.00,"evidence":[{"type":"DAMAGE_PHOTO","reference":"photo-01"}]}
        """;

    @Test void workflowEnforcesOwnershipTransitionsAndVersionAndRecordsActivity() throws Exception {
        users.findByEmailIgnoreCase("other@example.test").orElseGet(() -> users.save(new User("Other", "other@example.test", encoder.encode(PASSWORD), Role.CLAIMANT)));
        long officerId = users.findByEmailIgnoreCase(OFFICER).orElseThrow().getId();
        String created = mvc.perform(post("/api/v1/claims").with(httpBasic(CLAIMANT,PASSWORD)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(SUBMISSION)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SUBMITTED")).andExpect(jsonPath("$.evidence[0].type").value("DAMAGE_PHOTO"))
                .andReturn().getResponse().getContentAsString();
        JsonNode claim = json.readTree(created); long id = claim.get("id").asLong(); long version = claim.get("version").asLong();
        mvc.perform(get("/api/v1/claims/" + id).with(httpBasic("other@example.test", PASSWORD))).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/claims").with(httpBasic("other@example.test", PASSWORD)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content.length()").value(0));
        mvc.perform(post("/api/v1/claims/" + id + "/assign").with(httpBasic(CLAIMANT,PASSWORD)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"version\":" + version + ",\"officerId\":" + officerId + "}"))
                .andExpect(status().isForbidden());
        claim = action(id, "assign", OFFICER, "{\"version\":" + version + ",\"officerId\":" + officerId + "}", 200);
        assertThat(claim.get("status").asText()).isEqualTo("UNDER_REVIEW");
        long assignedVersion = claim.get("version").asLong();
        action(id, "decision", OFFICER, "{\"version\":" + version + ",\"decision\":\"APPROVED\",\"note\":\"Reviewed\"}", 409);
        claim = action(id, "request-info", OFFICER, "{\"version\":" + assignedVersion + ",\"note\":\"Please provide quotation\"}", 200);
        assertThat(claim.get("status").asText()).isEqualTo("MORE_INFO_REQUIRED");
        action(id, "decision", OFFICER, "{\"version\":" + claim.get("version").asLong() + ",\"decision\":\"APPROVED\",\"note\":\"Reviewed\"}", 409);
        claim = action(id, "additional-info", CLAIMANT, "{\"version\":" + claim.get("version").asLong() + ",\"note\":\"Added quote\",\"evidence\":[{\"type\":\"REPAIR_QUOTATION\",\"reference\":\"quote-01\"}]}", 200);
        claim = action(id, "decision", OFFICER, "{\"version\":" + claim.get("version").asLong() + ",\"decision\":\"APPROVED\",\"note\":\"Reviewed\"}", 200);
        assertThat(claim.get("status").asText()).isEqualTo("APPROVED");
        assertThat(claim.get("activities").size()).isEqualTo(5);
        assertThat(claim.get("evidence").size()).isEqualTo(2);
        mvc.perform(get("/api/v1/me").with(httpBasic(OFFICER,PASSWORD))).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(officerId));
        mvc.perform(get("/api/v1/dashboard/summary").with(httpBasic(OFFICER,PASSWORD))).andExpect(status().isOk())
                .andExpect(jsonPath("$.countsByStatus.APPROVED").value(1));
        mvc.perform(get("/api/v1/dashboard/summary").with(httpBasic(CLAIMANT,PASSWORD))).andExpect(status().isForbidden());
        action(id, "decision", OFFICER, "{\"version\":" + claim.get("version").asLong() + ",\"decision\":\"REJECTED\",\"note\":\"Again\"}", 409);
    }
    @Test void validatesInputAndRequiresCsrf() throws Exception {
        mvc.perform(post("/api/v1/claims").with(httpBasic(CLAIMANT,PASSWORD)).contentType(MediaType.APPLICATION_JSON).content(SUBMISSION)).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/claims").with(httpBasic(CLAIMANT,PASSWORD)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(SUBMISSION.replace("2500.00", "-1"))).andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/claims").with(httpBasic(OFFICER,PASSWORD)).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(SUBMISSION)).andExpect(status().isForbidden());
    }
    @Test @Transactional void onlyAssignedOfficerCanDecideAndReviewEvidence() throws Exception {
        users.findByEmailIgnoreCase("other-officer@example.test").orElseGet(() -> users.save(new User("Other officer", "other-officer@example.test", encoder.encode(PASSWORD), Role.OFFICER)));
        long officerId = users.findByEmailIgnoreCase(OFFICER).orElseThrow().getId();
        String created = mvc.perform(post("/api/v1/claims").with(httpBasic(CLAIMANT,PASSWORD)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(SUBMISSION)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        JsonNode claim = json.readTree(created);
        long id = claim.get("id").asLong(), evidenceId = claim.get("evidence").get(0).get("id").asLong();
        claim = action(id, "assign", OFFICER, "{\"version\":" + claim.get("version").asLong() + ",\"officerId\":" + officerId + "}", 200);
        long version = claim.get("version").asLong();
        action(id, "decision", "other-officer@example.test", "{\"version\":" + version + ",\"decision\":\"APPROVED\",\"note\":\"Reviewed\"}", 403);
        action(id, "evidence/" + evidenceId + "/review", "other-officer@example.test", "{\"version\":" + version + ",\"status\":\"REJECTED\",\"note\":\"Not usable\"}", 403);
        claim = action(id, "evidence/" + evidenceId + "/review", OFFICER, "{\"version\":" + version + ",\"status\":\"REJECTED\",\"note\":\"Not usable\"}", 200);
        assertThat(claim.get("version").asLong()).isGreaterThan(version);
        assertThat(claim.get("evidence").get(0).get("verificationStatus").asText()).isEqualTo("REJECTED");
        assertThat(claim.get("activities").get(2).get("action").asText()).isEqualTo("EVIDENCE_REJECTED");
        mvc.perform(get("/api/v1/claims/" + id + "/triage").with(httpBasic(CLAIMANT,PASSWORD))).andExpect(status().isOk())
                .andExpect(jsonPath("$.findings.length()").value(2));
    }
    @Test @Transactional void dashboardExcludesFinalClaimsAndPaginationHasStableShape() throws Exception {
        var before = json.readTree(mvc.perform(get("/api/v1/dashboard/summary").with(httpBasic(OFFICER,PASSWORD)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        BigDecimal initial = before.get("outstandingProvisionalExposure").decimalValue();
        JsonNode claim = json.readTree(mvc.perform(post("/api/v1/claims").with(httpBasic(CLAIMANT,PASSWORD)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(SUBMISSION)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        mvc.perform(get("/api/v1/claims?page=0&size=1").with(httpBasic(CLAIMANT,PASSWORD))).andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1)).andExpect(jsonPath("$.number").value(0));
        mvc.perform(get("/api/v1/claims?size=101").with(httpBasic(CLAIMANT,PASSWORD))).andExpect(status().isBadRequest());
        var open = json.readTree(mvc.perform(get("/api/v1/dashboard/summary").with(httpBasic(OFFICER,PASSWORD)))
                .andReturn().getResponse().getContentAsString());
        assertThat(open.get("outstandingProvisionalExposure").decimalValue()).isEqualByComparingTo(initial.add(new BigDecimal("2500")));
        long id = claim.get("id").asLong(), officerId = users.findByEmailIgnoreCase(OFFICER).orElseThrow().getId();
        claim = action(id, "assign", OFFICER, "{\"version\":" + claim.get("version").asLong() + ",\"officerId\":" + officerId + "}", 200);
        action(id, "decision", OFFICER, "{\"version\":" + claim.get("version").asLong() + ",\"decision\":\"APPROVED\",\"note\":\"Reviewed\"}", 200);
        var after = json.readTree(mvc.perform(get("/api/v1/dashboard/summary").with(httpBasic(OFFICER,PASSWORD)))
                .andReturn().getResponse().getContentAsString());
        assertThat(after.get("outstandingProvisionalExposure").decimalValue()).isEqualByComparingTo(initial);
    }
    private JsonNode action(long id, String path, String user, String body, int expected) throws Exception {
        var response = mvc.perform(post("/api/v1/claims/" + id + "/" + path).with(httpBasic(user,PASSWORD)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().is(expected)).andReturn().getResponse();
        return json.readTree(response.getContentAsString());
    }
}
