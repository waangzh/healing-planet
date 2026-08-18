package com.healingplanet.ai.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.time.Clock;

@Configuration
@Profile("eval")
public class EvalConfiguration {

    @Bean("ragClock")
    Clock ragClock(RagProperties properties) {
        var eval = properties.getEval();
        return Clock.fixed(eval.getClockInstant(), eval.getClockZone());
    }
}
