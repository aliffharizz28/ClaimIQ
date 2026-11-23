package com.claimiq;

import com.claimiq.identity.Role;
import com.claimiq.identity.User;
import com.claimiq.identity.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("demo") @Transactional
class TriageWorkflowTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder encoder;
    private static final String OWNER = "claimant@example.test", OFFICER = "officer@example.test", PASS = "demo-password";

    @Test void gapsDuplicatesAndFreshAnalysisAreExplainableAndProtected() throws Exception {
        users.findByEmailIgnoreCase("triage-other@example.test").orElseGet(() -> users.save(new User("Other", "triage-other@example.test", encoder.encode(PASS), Role.CLAIMANT)));
        long officerId = users.findByEmailIgnoreCase(OFFICER).orElseThrow().getId();
        JsonNode first = submit("abc-987", "[]", OWNER);
        long id = first.get("id").asLong();
        JsonNode triage = getTriage(id, OWNER);
        assertThat(triage.get("priority").asText()).isEqualTo("MEDIUM");
        assertThat(triage.get("findings").size()).isEqualTo(2);
        assertThat(triage.get("findings").get(0).get("type").asText()).isEqualTo("EVIDENCE_GAP");
        mvc.perform(get("/api/v1/claims/" + id + "/triage").with(httpBasic("triage-other@example.test",PASS))).andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/claims/" + id + "/triage").with(httpBasic(OWNER,PASS)).with(csrf())).andExpect(status().isForbidden());

        JsonNode second = submit("ABC / 987", "[{\"type\":\"DAMAGE_PHOTO\",\"reference\":\"p\"},{\"type\":\"REPAIR_QUOTATION\",\"reference\":\"q\"}]", OWNER);
        JsonNode duplicate = getTriage(second.get("id").asLong(), OFFICER);
        assertThat(duplicate.get("priority").asText()).isEqualTo("HIGH");
        assertThat(duplicate.get("findings").get(0).get("relatedClaimId").asLong()).isEqualTo(id);
        assertThat(duplicate.get("findings").get(0).get("type").asText()).isEqualTo("POSSIBLE_DUPLICATE");
        JsonNode other = submit("ABC987", "[{\"type\":\"DAMAGE_PHOTO\",\"reference\":\"p\"},{\"type\":\"REPAIR_QUOTATION\",\"reference\":\"q\"}]", "triage-other@example.test");
        assertThat(getTriage(other.get("id").asLong(), "triage-other@example.test").get("priority").asText()).isEqualTo("NORMAL");

        JsonNode assigned = postAction(id, "assign", OFFICER, "{\"version\":" + first.get("version").asLong() + ",\"officerId\":" + officerId + "}");
        JsonNode requested = postAction(id, "request-info", OFFICER, "{\"version\":" + assigned.get("version").asLong() + ",\"note\":\"Evidence please\"}");
        postAction(id, "additional-info", OWNER, "{\"version\":" + requested.get("version").asLong() + ",\"note\":\"Added both\",\"evidence\":[{\"type\":\"DAMAGE_PHOTO\",\"reference\":\"p\"},{\"type\":\"REPAIR_QUOTATION\",\"reference\":\"q\"}]}");
        JsonNode refreshed = getTriage(id, OFFICER);
        assertThat(refreshed.get("id").asLong()).isGreaterThan(triage.get("id").asLong());
        assertThat(refreshed.get("priority").asText()).isEqualTo("HIGH");
        assertThat(refreshed.get("findings").size()).isEqualTo(1);
        mvc.perform(post("/api/v1/claims/" + other.get("id").asLong() + "/triage").with(httpBasic(OFFICER,PASS)).with(csrf())).andExpect(status().isOk());
    }
    @Test void recentVehicleClaimsUseInclusiveThirtyDayWindowAndSameClaimant() throws Exception {
        users.findByEmailIgnoreCase("recent-other@example.test").orElseGet(() -> users.save(new User("Other", "recent-other@example.test", encoder.encode(PASS), Role.CLAIMANT)));
        String complete = "[{\"type\":\"DAMAGE_PHOTO\",\"reference\":\"p\"},{\"type\":\"REPAIR_QUOTATION\",\"reference\":\"q\"}]";
        JsonNode first = submit("JUJ 4085", complete, OWNER, "2024-03-01");
        JsonNode nearby = submit("juj-4085", complete, OWNER, "2024-03-09");
        JsonNode finding = getTriage(nearby.get("id").asLong(), OFFICER);
        assertThat(finding.get("priority").asText()).isEqualTo("MEDIUM");
        assertThat(finding.get("ruleVersion").asText()).isEqualTo("motor-v3");
        assertThat(finding.get("findings").get(0).get("type").asText()).isEqualTo("RECENT_VEHICLE_CLAIM");
        assertThat(finding.get("findings").get(0).get("relatedClaimId").asLong()).isEqualTo(first.get("id").asLong());
        assertThat(finding.get("findings").get(0).get("explanation").asText()).contains("8 days", "Separate incidents are possible");

        JsonNode boundary = submit("BOUNDARY", complete, OWNER, "2024-01-01");
        JsonNode day30 = submit("boundary", complete, OWNER, "2024-01-31");
        assertThat(getTriage(day30.get("id").asLong(), OFFICER).get("findings").get(0).get("relatedClaimId").asLong()).isEqualTo(boundary.get("id").asLong());
        submit("OUTSIDE", complete, OWNER, "2024-01-01");
        JsonNode day31 = submit("OUTSIDE", complete, OWNER, "2024-02-01");
        assertThat(getTriage(day31.get("id").asLong(), OWNER).get("priority").asText()).isEqualTo("NORMAL");
        JsonNode differentOwner = submit("JUJ4085", complete, "recent-other@example.test", "2024-03-10");
        assertThat(getTriage(differentOwner.get("id").asLong(), "recent-other@example.test").get("priority").asText()).isEqualTo("NORMAL");
        JsonNode earlier = submit("JUJ4085", complete, OWNER, "2024-02-29");
        assertThat(getTriage(earlier.get("id").asLong(), OFFICER).get("findings").get(0).get("type").asText()).isEqualTo("RECENT_VEHICLE_CLAIM");
        mvc.perform(post("/api/v1/claims/" + first.get("id").asLong() + "/triage").with(httpBasic(OFFICER,PASS)).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.priority").value("MEDIUM"));
    }
    @Test void repeatedLongNarrativeIsAReviewPromptWithinTheSameAccountAndWindow() throws Exception {
        users.findByEmailIgnoreCase("narrative-other@example.test").orElseGet(() -> users.save(new User("Other", "narrative-other@example.test", encoder.encode(PASS), Role.CLAIMANT)));
        String complete = "[{\"type\":\"DAMAGE_PHOTO\",\"reference\":\"p\"},{\"type\":\"REPAIR_QUOTATION\",\"reference\":\"q\"}]";
        String text = "My vehicle was hit from behind at a traffic light near the shopping centre.";
        JsonNode first = submit("NAR001", complete, OWNER, "2024-05-01", text);
        JsonNode second = submit("NAR002", complete, OWNER, "2024-05-31", text.toUpperCase());
        JsonNode triage = getTriage(second.get("id").asLong(), OFFICER);
        assertThat(triage.get("priority").asText()).isEqualTo("MEDIUM");
        assertThat(triage.get("findings").get(0).get("type").asText()).isEqualTo("REPEATED_NARRATIVE");
        assertThat(triage.get("findings").get(0).get("relatedClaimId").asLong()).isEqualTo(first.get("id").asLong());
        assertThat(triage.get("findings").get(0).get("explanation").asText()).contains("does not establish a duplicate or fraud");
        JsonNode other = submit("NAR003", complete, "narrative-other@example.test", "2024-05-02", text);
        assertThat(getTriage(other.get("id").asLong(), "narrative-other@example.test").get("priority").asText()).isEqualTo("NORMAL");
        JsonNode outside = submit("NAR004", complete, OWNER, "2024-03-31", text);
        assertThat(getTriage(outside.get("id").asLong(), OWNER).get("priority").asText()).isEqualTo("NORMAL");
        submit("SHORT01", complete, OWNER, "2024-06-01", "Rear impact");
        JsonNode shortRepeat = submit("SHORT02", complete, OWNER, "2024-06-02", "Rear impact");
        assertThat(getTriage(shortRepeat.get("id").asLong(), OWNER).get("priority").asText()).isEqualTo("NORMAL");
        JsonNode changedText = submit("NAR005", complete, OWNER, "2024-05-02", text + " Additional detail.");
        assertThat(getTriage(changedText.get("id").asLong(), OWNER).get("priority").asText()).isEqualTo("NORMAL");
    }
    private JsonNode submit(String registration, String evidence, String user) throws Exception {
        return submit(registration, evidence, user, "2024-01-02");
    }
    private JsonNode submit(String registration, String evidence, String user, String incidentDate) throws Exception {
        return submit(registration, evidence, user, incidentDate, "Incident");
    }
    private JsonNode submit(String registration, String evidence, String user, String incidentDate, String description) throws Exception {
        String body = "{\"incidentDate\":\"" + incidentDate + "\",\"vehicleRegistration\":\"" + registration + "\",\"description\":" + json.writeValueAsString(description) + ",\"claimedAmount\":100,\"evidence\":" + evidence + "}";
        String result = mvc.perform(post("/api/v1/claims").with(httpBasic(user,PASS)).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(result);
    }
    private JsonNode getTriage(long id, String user) throws Exception {
        return json.readTree(mvc.perform(get("/api/v1/claims/" + id + "/triage").with(httpBasic(user,PASS))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }
    private JsonNode postAction(long id, String path, String user, String body) throws Exception {
        return json.readTree(mvc.perform(post("/api/v1/claims/" + id + "/" + path).with(httpBasic(user,PASS)).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }
}
