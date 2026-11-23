package com.claimiq;

import com.claimiq.claim.Claim;
import com.claimiq.claim.ClaimRepository;
import com.claimiq.identity.UserRepository;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.assertThat;

class FileDatabaseRestartTest {
    @TempDir Path directory;

    @Test void existingClaimsSurviveRestartAndNormalizationIsBackfilled() {
        String url = "jdbc:h2:file:" + directory.resolve("restart-db").toAbsolutePath().toString().replace('\\', '/');
        Long id;
        try (ConfigurableApplicationContext first = start(url)) {
            var owner = first.getBean(UserRepository.class).findByEmailIgnoreCase("claimant@example.test").orElseThrow();
            Claim claim = first.getBean(ClaimRepository.class).saveAndFlush(new Claim(owner, LocalDate.of(2024, 2, 1), "ABC / 123", "Test incident", new BigDecimal("100.00")));
            id = claim.getId();
            var jdbc = first.getBean(JdbcTemplate.class);
            jdbc.execute("drop index if exists idx_claim_owner_date_registration");
            jdbc.execute("alter table claims drop column normalized_registration");
        }
        try (ConfigurableApplicationContext second = start(url)) {
            Claim stored = second.getBean(ClaimRepository.class).findById(id).orElseThrow();
            assertThat(stored.getNormalizedRegistration()).isEqualTo("ABC123");
            assertThat(stored.getClaimedAmount()).isEqualByComparingTo("100.00");
            Integer indexes = second.getBean(JdbcTemplate.class).queryForObject("select count(*) from information_schema.indexes where index_name = 'IDX_CLAIM_OWNER_DATE_REGISTRATION'", Integer.class);
            assertThat(indexes).isEqualTo(1);
        }
    }
    private ConfigurableApplicationContext start(String url) {
        return new SpringApplicationBuilder(ClaimIqApplication.class).run("--spring.datasource.url=" + url,
                "--spring.jpa.hibernate.ddl-auto=update", "--spring.profiles.active=demo", "--server.port=0");
    }
}
