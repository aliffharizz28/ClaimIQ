package com.claimiq.claim;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.boot.autoconfigure.validation.ValidationConfigurationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TimeConfig {
    @Bean Clock claimClock() { return Clock.system(ZoneId.of("Asia/Kuala_Lumpur")); }
    @Bean ValidationConfigurationCustomizer validationClock(Clock clock) {
        return configuration -> configuration.clockProvider(() -> clock);
    }
}
