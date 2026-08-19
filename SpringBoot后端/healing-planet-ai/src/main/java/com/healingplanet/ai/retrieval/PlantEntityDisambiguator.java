package com.healingplanet.ai.retrieval;

import com.healingplanet.ai.config.RagProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;
import java.util.stream.Collectors;

@Component
public class PlantEntityDisambiguator {

    private static final String SYSTEM_PROMPT = """
            你是植物实体消歧器。你的任务不是回答养护问题，而是判断用户提到的植物是否能安全映射到候选列表中的某一个标准植物。
            规则：
            1. 只能在提供的 canonicalPlantId 候选中选择一个，或返回 UNKNOWN / AMBIGUOUS。
            2. 泛类词、类别词、场景词、修饰词不能映射到具体植物，例如“绿植”“盆栽”“花卉”“植物”“室内植物”都应返回 UNKNOWN。
            3. 如果用户表达的是未知复合名称、未登记别称、无法确认的新名字，也返回 UNKNOWN。
            4. 只有在你能较有把握地判断用户确实想表达某个候选植物时，才返回 KNOWN。
            5. 如果能确认提及指向候选范围，但上下文不足以在多个候选中唯一选择，返回 AMBIGUOUS。
            6. 输出必须符合给定结构，不要输出额外文本。
            """;

    private final RagProperties.EntityResolution properties;
    private final StructuredCaller caller;
    private final RetrievalMetrics metrics;
    private final FailureCircuitBreaker circuitBreaker;
    private final Map<String, Decision> cache = new ConcurrentHashMap<>();

    @Autowired
    public PlantEntityDisambiguator(RagProperties ragProperties, StructuredCaller caller,
                                    RetrievalMetrics metrics) {
        this(ragProperties, caller, metrics, new FailureCircuitBreaker(
                ragProperties.getEntityResolution().getCircuitBreakerFailureThreshold(),
                ragProperties.getEntityResolution().getCircuitBreakerOpenMillis()));
    }

    PlantEntityDisambiguator(RagProperties ragProperties, StructuredCaller caller) {
        this(ragProperties, caller, null, new FailureCircuitBreaker(
                ragProperties.getEntityResolution().getCircuitBreakerFailureThreshold(),
                ragProperties.getEntityResolution().getCircuitBreakerOpenMillis()));
    }

    PlantEntityDisambiguator(RagProperties ragProperties, StructuredCaller caller,
                             RetrievalMetrics metrics, FailureCircuitBreaker circuitBreaker) {
        this.properties = ragProperties.getEntityResolution();
        this.caller = caller;
        this.metrics = metrics;
        this.circuitBreaker = circuitBreaker;
    }

    public Decision disambiguate(String query, String mention, List<CandidateOption> candidates) {
        if (!properties.isLlmEnabled() || candidates.isEmpty()) {
            return Decision.skipped("llm_disabled_or_no_candidates");
        }
        List<CandidateOption> limited = candidates.stream()
                .limit(Math.max(1, properties.getLlmMaxCandidates()))
                .toList();
        String cacheKey = cacheKey(query, mention, limited);
        Decision cached = cache.get(cacheKey);
        if (cached != null) return cached;
        Set<String> allowedIds = limited.stream()
                .map(CandidateOption::canonicalPlantId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        try {
            LlmDecision result = timedCall(() -> circuitBreaker.execute(
                    () -> caller.call(SYSTEM_PROMPT, buildUserPrompt(query, mention, limited))));
            if (result == null || result.decision() == null) {
                return Decision.unavailable("llm_empty_response");
            }
            String action = result.decision().trim().toUpperCase(Locale.ROOT);
            if ("AMBIGUOUS".equals(action)) {
                return Decision.ambiguous("llm_ambiguous");
            }
            if ("UNKNOWN".equals(action)) {
                return Decision.unknown("llm_rejected_or_unknown");
            }
            if (!"KNOWN".equals(action)) return Decision.unavailable("llm_invalid_decision");
            String canonicalPlantId = result.canonicalPlantId() == null ? "" : result.canonicalPlantId().trim();
            double confidence = result.confidence() == null ? 0 : result.confidence();
            if (!allowedIds.contains(canonicalPlantId)) {
                return Decision.unavailable("llm_returned_invalid_candidate");
            }
            if (confidence < properties.getLlmConfidenceThreshold()) {
                return limited.size() > 1
                        ? Decision.ambiguous("llm_confidence_too_low")
                        : Decision.unknown("llm_confidence_too_low");
            }
            return cache(cacheKey, Decision.known(canonicalPlantId, confidence));
        } catch (CircuitOpenException ignored) {
            return Decision.unavailable("llm_disambiguation_circuit_open");
        } catch (RuntimeException ignored) {
            return Decision.unavailable("llm_disambiguation_failed");
        }
    }

    private LlmDecision timedCall(java.util.function.Supplier<LlmDecision> operation) {
        return metrics == null ? operation.get() : metrics.time("entity_disambiguation", "llm", operation);
    }

    private Decision cache(String key, Decision value) {
        if (!value.known()) return value;
        if (cache.size() >= properties.getLlmCacheMaxEntries()) cache.clear();
        cache.putIfAbsent(key, value);
        return value;
    }

    private String cacheKey(String query, String mention, List<CandidateOption> candidates) {
        String options = candidates.stream().map(candidate -> candidate.canonicalPlantId()
                + ":" + String.join(",", candidate.names())).collect(Collectors.joining("|"));
        return query + "\u0000" + (mention == null ? "" : mention) + "\u0000" + options;
    }

    private String buildUserPrompt(String query, String mention, List<CandidateOption> candidates) {
        String candidateLines = candidates.stream()
                .map(candidate -> "- canonicalPlantId=" + candidate.canonicalPlantId()
                        + "; names=" + String.join("/", candidate.names())
                        + "; vectorScore=" + format(candidate.vectorScore())
                        + "; lexicalScore=" + format(candidate.lexicalScore()))
                .collect(Collectors.joining("\n"));
        return """
                用户原问题：
                %s

                抽取到的疑似植物提及：
                %s

                允许选择的候选：
                %s

                请返回：
                - decision: KNOWN、UNKNOWN 或 AMBIGUOUS
                - canonicalPlantId: 仅当 decision=KNOWN 时填写候选中的一个 ID，否则留空
                - confidence: 0 到 1
                - reason: 简短说明
                """.formatted(query, mention == null || mention.isBlank() ? "(空)" : mention, candidateLines);
    }

    private String format(double value) {
        return String.format(Locale.ROOT, "%.4f", value);
    }

    @FunctionalInterface
    public interface StructuredCaller {
        LlmDecision call(String systemPrompt, String userPrompt);
    }

    static final class FailureCircuitBreaker {
        private static final long HALF_OPEN = Long.MAX_VALUE;
        private final int failureThreshold;
        private final long openNanos;
        private final LongSupplier nanoClock;
        private final AtomicInteger failures = new AtomicInteger();
        private final AtomicLong openUntil = new AtomicLong();

        FailureCircuitBreaker(int failureThreshold, long openMillis) {
            this(failureThreshold, openMillis, System::nanoTime);
        }

        FailureCircuitBreaker(int failureThreshold, long openMillis, LongSupplier nanoClock) {
            this.failureThreshold = Math.max(1, failureThreshold);
            this.openNanos = java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(Math.max(1, openMillis));
            this.nanoClock = nanoClock;
        }

        <T> T execute(java.util.function.Supplier<T> operation) {
            acquire();
            try {
                T result = operation.get();
                failures.set(0);
                openUntil.set(0);
                return result;
            } catch (RuntimeException exception) {
                int count = failures.incrementAndGet();
                if (openUntil.get() == HALF_OPEN || count >= failureThreshold) {
                    openUntil.set(nanoClock.getAsLong() + openNanos);
                }
                throw exception;
            }
        }

        private void acquire() {
            long state = openUntil.get();
            if (state == 0) return;
            long now = nanoClock.getAsLong();
            if (state == HALF_OPEN || now < state || !openUntil.compareAndSet(state, HALF_OPEN)) {
                throw new CircuitOpenException();
            }
        }
    }

    static final class CircuitOpenException extends RuntimeException { }

    public record CandidateOption(String canonicalPlantId, Set<String> names, double vectorScore, double lexicalScore) { }

    public enum DecisionKind { KNOWN, AMBIGUOUS, UNKNOWN, UNAVAILABLE, SKIPPED }

    public record Decision(DecisionKind kind, String canonicalPlantId, double confidence,
                           String reason, boolean attempted) {
        public boolean known() {
            return kind == DecisionKind.KNOWN;
        }

        public boolean ambiguous() {
            return kind == DecisionKind.AMBIGUOUS;
        }

        public boolean unavailable() {
            return kind == DecisionKind.UNAVAILABLE;
        }

        static Decision known(String canonicalPlantId, double confidence) {
            return new Decision(DecisionKind.KNOWN, canonicalPlantId, confidence, "", true);
        }

        static Decision ambiguous(String reason) {
            return new Decision(DecisionKind.AMBIGUOUS, "", 0, reason, true);
        }

        static Decision unknown(String reason) {
            return new Decision(DecisionKind.UNKNOWN, "", 0, reason, true);
        }

        static Decision unavailable(String reason) {
            return new Decision(DecisionKind.UNAVAILABLE, "", 0, reason, true);
        }

        static Decision skipped(String reason) {
            return new Decision(DecisionKind.SKIPPED, "", 0, reason, false);
        }
    }

    public record LlmDecision(String decision, String canonicalPlantId, Double confidence, String reason) { }
}
