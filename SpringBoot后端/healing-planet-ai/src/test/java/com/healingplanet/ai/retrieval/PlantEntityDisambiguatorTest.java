package com.healingplanet.ai.retrieval;

import com.healingplanet.ai.config.RagProperties;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class PlantEntityDisambiguatorTest {

    @Test
    void shouldAcceptKnownCandidateReturnedByLlmWhenConfidenceIsHighEnough() {
        RagProperties properties = new RagProperties();
        PlantEntityDisambiguator disambiguator = new PlantEntityDisambiguator(properties,
                (systemPrompt, userPrompt) -> new PlantEntityDisambiguator.LlmDecision("KNOWN", "1", 0.91, "typo"));

        var result = disambiguator.disambiguate("绿箩能一直晒大太阳不？", "绿箩", List.of(
                new PlantEntityDisambiguator.CandidateOption("1", Set.of("绿萝"), 0.44, 0.50),
                new PlantEntityDisambiguator.CandidateOption("10", Set.of("芦荟"), 0.39, 0.21)
        ));

        assertThat(result.attempted()).isTrue();
        assertThat(result.known()).isTrue();
        assertThat(result.canonicalPlantId()).isEqualTo("1");
    }

    @Test
    void shouldRejectInvalidCandidateReturnedByLlm() {
        RagProperties properties = new RagProperties();
        PlantEntityDisambiguator disambiguator = new PlantEntityDisambiguator(properties,
                (systemPrompt, userPrompt) -> new PlantEntityDisambiguator.LlmDecision("KNOWN", "999", 0.95, "bad"));

        var result = disambiguator.disambiguate("绿箩能一直晒大太阳不？", "绿箩", List.of(
                new PlantEntityDisambiguator.CandidateOption("1", Set.of("绿萝"), 0.44, 0.50)
        ));

        assertThat(result.attempted()).isTrue();
        assertThat(result.known()).isFalse();
        assertThat(result.reason()).isEqualTo("llm_returned_invalid_candidate");
    }

    @Test
    void shouldRejectLowConfidenceLlmResult() {
        RagProperties properties = new RagProperties();
        PlantEntityDisambiguator disambiguator = new PlantEntityDisambiguator(properties,
                (systemPrompt, userPrompt) -> new PlantEntityDisambiguator.LlmDecision("KNOWN", "1", 0.61, "weak"));

        var result = disambiguator.disambiguate("绿箩能一直晒大太阳不？", "绿箩", List.of(
                new PlantEntityDisambiguator.CandidateOption("1", Set.of("绿萝"), 0.44, 0.50)
        ));

        assertThat(result.attempted()).isTrue();
        assertThat(result.known()).isFalse();
        assertThat(result.reason()).isEqualTo("llm_confidence_too_low");
    }

    @Test
    void shouldFailClosedWhenStructuredCallThrows() {
        RagProperties properties = new RagProperties();
        PlantEntityDisambiguator disambiguator = new PlantEntityDisambiguator(properties,
                (systemPrompt, userPrompt) -> {
                    throw new IllegalStateException("boom");
                });

        var result = disambiguator.disambiguate("绿箩能一直晒大太阳不？", "绿箩", List.of(
                new PlantEntityDisambiguator.CandidateOption("1", Set.of("绿萝"), 0.44, 0.50)
        ));

        assertThat(result.attempted()).isTrue();
        assertThat(result.known()).isFalse();
        assertThat(result.reason()).isEqualTo("llm_disambiguation_failed");
    }

    @Test
    void shouldCacheValidatedDecisionsForTheSameQueryAndCandidates() {
        RagProperties properties = new RagProperties();
        AtomicInteger calls = new AtomicInteger();
        PlantEntityDisambiguator disambiguator = new PlantEntityDisambiguator(properties,
                (systemPrompt, userPrompt) -> {
                    calls.incrementAndGet();
                    return new PlantEntityDisambiguator.LlmDecision("KNOWN", "1", 0.91, "typo");
                });
        List<PlantEntityDisambiguator.CandidateOption> candidates = List.of(
                new PlantEntityDisambiguator.CandidateOption("1", Set.of("绿萝"), 0.44, 0.50));

        disambiguator.disambiguate("绿箩能一直晒大太阳不？", "绿箩", candidates);
        disambiguator.disambiguate("绿箩能一直晒大太阳不？", "绿箩", candidates);

        assertThat(calls).hasValue(1);
    }
}
