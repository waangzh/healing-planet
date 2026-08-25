package com.healingplanet.ai;

import com.healingplanet.ai.config.RagProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableConfigurationProperties(RagProperties.class)
@EnableScheduling
public class HealingPlanetAiApplication {

    public static void main(String[] args) {
        SpringApplication.run(HealingPlanetAiApplication.class, args);
    }
}
