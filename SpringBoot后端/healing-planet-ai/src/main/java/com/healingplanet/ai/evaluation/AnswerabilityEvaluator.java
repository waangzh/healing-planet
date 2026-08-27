package com.healingplanet.ai.evaluation;

import com.healingplanet.ai.domain.EntityResolutionDiagnostics;
import com.healingplanet.ai.domain.Evidence;
import com.healingplanet.ai.domain.EvidenceType;
import com.healingplanet.ai.query.StateNeed;
import com.healingplanet.ai.retrieval.RetrievalRequest;
import com.healingplanet.ai.retrieval.SourcePlan;
import org.springframework.stereotype.Component;

import java.util.List;

/** Makes safe outcomes from retrieved evidence rather than a pre-retrieval classifier. */
@Component
public class AnswerabilityEvaluator {
    private static final double MIN_RETRIEVAL_RELEVANCE = 0.45d;
    private static final double MIN_RERANK_RELEVANCE = 0.40d;
    private static final double MIN_ALIGNED_SEMANTIC_RELEVANCE = 0.30d;
    private static final double MIN_ALIGNED_FINAL_RELEVANCE = 0.60d;
    private static final double STRONG_RECOVERY_RELEVANCE = 0.60d;

    public Assessment evaluate(RetrievalRequest request, List<Evidence> evidence,
                               EntityResolutionDiagnostics entityResolution) {
        if (entityResolution != null) {
            return switch (entityResolution.resolutionKind()) {
                case "CONFLICT" -> new Assessment(Answerability.ENTITY_CONFLICT, "entity_conflict");
                case "AMBIGUOUS" -> new Assessment(Answerability.ENTITY_AMBIGUOUS, "entity_ambiguous");
                case "UNKNOWN" -> new Assessment(Answerability.ENTITY_UNKNOWN, "entity_unknown");
                default -> evaluateEvidence(request, evidence);
            };
        }
        return evaluateEvidence(request, evidence);
    }

    private Assessment evaluateEvidence(RetrievalRequest request, List<Evidence> evidence) {
        boolean hasLive = evidence.stream().anyMatch(item -> item.type() == EvidenceType.LIVE_STATE);
        boolean hasHistory = evidence.stream().anyMatch(item -> item.type() == EvidenceType.SENSOR_HISTORY);
        if (request.plan().searchState()) {
            boolean currentRequired = request.stateNeeds().contains(StateNeed.CURRENT)
                    || request.stateNeeds().contains(StateNeed.FRESHNESS)
                    || request.stateNeeds().contains(StateNeed.DECISION_SUPPORT);
            if (currentRequired && !hasLive || request.stateNeeds().contains(StateNeed.HISTORY) && !hasHistory) {
                return new Assessment(Answerability.STATE_UNAVAILABLE, "required_state_evidence_missing");
            }
            boolean immediateDecision = request.stateNeeds().contains(StateNeed.DECISION_SUPPORT)
                    || request.stateNeeds().contains(StateNeed.FRESHNESS);
            if (immediateDecision && evidence.stream().filter(item -> item.type() == EvidenceType.LIVE_STATE)
                    .anyMatch(item -> Boolean.TRUE.equals(item.metadata().get("stale")))) {
                return new Assessment(Answerability.STATE_STALE, "live_state_stale");
            }
        }
        Assessment missingSource = requiredSourceAssessment(request.sourcePlan(), evidence);
        if (missingSource != null) return missingSource;

        boolean resolvedEntity = request.entityResolution() != null
                && request.entityResolution().hasResolvedEntities();
        boolean relevantEvidence = evidence.stream().anyMatch(item -> relevant(request, item, resolvedEntity));
        boolean strongRecoveryEvidence = evidence.stream().anyMatch(this::stronglyRelevant);
        if (relevantEvidence && (request.analysis().plantDomainConfidence() >= 0.3d
                || resolvedEntity || strongRecoveryEvidence)) {
            return new Assessment(Answerability.ANSWERABLE, "relevant_evidence_selected");
        }
        return request.analysis().plantDomainConfidence() < 0.3d && !resolvedEntity
                ? new Assessment(Answerability.OUT_OF_SCOPE, "low_plant_hint_without_strong_relevant_evidence")
                : new Assessment(Answerability.INSUFFICIENT_EVIDENCE, "selected_evidence_below_relevance_threshold");
    }

    private Assessment requiredSourceAssessment(SourcePlan sourcePlan, List<Evidence> evidence) {
        boolean hasKnowledge = evidence.stream().anyMatch(item -> item.type() == EvidenceType.CARE_GUIDE
                || item.type() == EvidenceType.PLANT_KNOWLEDGE
                || item.type() == EvidenceType.DISEASE_KNOWLEDGE);
        boolean hasCommunity = evidence.stream().anyMatch(item -> item.type() == EvidenceType.COMMUNITY_POST);
        if (sourcePlan.knowledge().required() && !hasKnowledge) {
            return new Assessment(Answerability.INSUFFICIENT_EVIDENCE, "required_knowledge_evidence_missing");
        }
        if (sourcePlan.community().required() && !hasCommunity) {
            return new Assessment(Answerability.INSUFFICIENT_EVIDENCE, "required_community_evidence_missing");
        }
        return null;
    }

    private boolean relevant(RetrievalRequest request, Evidence evidence, boolean resolvedEntity) {
        if (evidence.type() == EvidenceType.LIVE_STATE || evidence.type() == EvidenceType.SENSOR_HISTORY
                || evidence.type() == EvidenceType.VISUAL_OBSERVATION
                || evidence.type() == EvidenceType.SENSOR_CONSISTENCY) return true;
        boolean aligned = topicAligned(request, evidence) || entityAligned(request, evidence, resolvedEntity);
        double retrieval = score(evidence.retrievalScore());
        double rerank = score(evidence.rerankScore());
        double semantic = Math.max(retrieval, rerank);
        return retrieval >= MIN_RETRIEVAL_RELEVANCE || rerank >= MIN_RERANK_RELEVANCE
                || aligned && semantic >= MIN_ALIGNED_SEMANTIC_RELEVANCE
                && score(evidence.finalScore()) >= MIN_ALIGNED_FINAL_RELEVANCE;
    }

    private boolean stronglyRelevant(Evidence evidence) {
        return Math.max(score(evidence.retrievalScore()), score(evidence.rerankScore()))
                >= STRONG_RECOVERY_RELEVANCE;
    }

    private boolean topicAligned(RetrievalRequest request, Evidence evidence) {
        Object value = evidence.metadata().get("knowledgeType");
        return value != null && request.topicHints().stream().anyMatch(topic -> topic.equalsIgnoreCase(value.toString()));
    }

    private boolean entityAligned(RetrievalRequest request, Evidence evidence, boolean resolvedEntity) {
        if (!resolvedEntity) return false;
        Object value = evidence.metadata().get("canonicalPlantId");
        return value != null && request.entityResolution().canonicalPlantIds().contains(value.toString());
    }

    private double score(Double value) {
        return value == null || !Double.isFinite(value) ? 0d : value;
    }

    public record Assessment(Answerability result, String reason) { }
}
