package com.healingplanet.ai.evaluation;

import com.healingplanet.ai.config.RagRuntimeConfig;
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
    public Assessment evaluate(RetrievalRequest request, List<Evidence> evidence,
                               EntityResolutionDiagnostics entityResolution, RagRuntimeConfig config) {
        if (entityResolution != null) {
            return switch (entityResolution.resolutionKind()) {
                case "CONFLICT" -> new Assessment(Answerability.ENTITY_CONFLICT, "entity_conflict");
                case "AMBIGUOUS" -> new Assessment(Answerability.ENTITY_AMBIGUOUS, "entity_ambiguous");
                case "UNKNOWN" -> new Assessment(Answerability.ENTITY_UNKNOWN, "entity_unknown");
                default -> evaluateEvidence(request, evidence, config.answerability());
            };
        }
        return evaluateEvidence(request, evidence, config.answerability());
    }

    private Assessment evaluateEvidence(RetrievalRequest request, List<Evidence> evidence,
                                        RagRuntimeConfig.Answerability thresholds) {
        if (!request.stateNeeds().isEmpty()
                && request.sourcePlan().state() == SourcePlan.SourceRequirement.FORBIDDEN) {
            return new Assessment(Answerability.INSUFFICIENT_EVIDENCE, "required_state_evidence_forbidden");
        }
        boolean hasLive = evidence.stream().anyMatch(item -> item.type() == EvidenceType.LIVE_STATE);
        boolean hasHistory = evidence.stream().anyMatch(item -> item.type() == EvidenceType.SENSOR_HISTORY);
        if (request.plan().searchState()) {
            boolean currentRequired = request.stateNeeds().contains(StateNeed.CURRENT)
                    || request.stateNeeds().contains(StateNeed.FRESHNESS)
                    || request.stateNeeds().contains(StateNeed.DECISION_SUPPORT);
            if (currentRequired && !hasLive || request.stateNeeds().contains(StateNeed.HISTORY) && !hasHistory) {
                return new Assessment(Answerability.STATE_UNAVAILABLE, "required_state_evidence_missing");
            }
            boolean immediateDecision = request.stateNeeds().contains(StateNeed.DECISION_SUPPORT);
            if (immediateDecision && evidence.stream().filter(item -> item.type() == EvidenceType.LIVE_STATE)
                    .anyMatch(item -> Boolean.TRUE.equals(item.metadata().get("stale")))) {
                return new Assessment(Answerability.STATE_STALE, "live_state_stale");
            }
        }
        Assessment missingSource = requiredSourceAssessment(request.sourcePlan(), evidence);
        if (missingSource != null) return missingSource;

        boolean resolvedEntity = request.entityResolution() != null
                && request.entityResolution().hasResolvedEntities();
        boolean relevantEvidence = evidence.stream()
                .anyMatch(item -> relevant(request, item, resolvedEntity, thresholds));
        boolean strongRecoveryEvidence = evidence.stream()
                .anyMatch(item -> stronglyRelevant(item, thresholds));
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

    private boolean relevant(RetrievalRequest request, Evidence evidence, boolean resolvedEntity,
                             RagRuntimeConfig.Answerability thresholds) {
        if (evidence.type() == EvidenceType.LIVE_STATE || evidence.type() == EvidenceType.SENSOR_HISTORY
                || evidence.type() == EvidenceType.VISUAL_OBSERVATION
                || evidence.type() == EvidenceType.SENSOR_CONSISTENCY) return true;
        boolean aligned = topicAligned(request, evidence) || entityAligned(request, evidence, resolvedEntity);
        double retrieval = score(evidence.retrievalScore());
        double rerank = score(evidence.rerankScore());
        double semantic = Math.max(retrieval, rerank);
        return retrieval >= thresholds.minRetrievalRelevance() || rerank >= thresholds.minRerankRelevance()
                || aligned && semantic >= thresholds.minAlignedSemanticRelevance()
                && score(evidence.finalScore()) >= thresholds.minAlignedFinalRelevance();
    }

    private boolean stronglyRelevant(Evidence evidence, RagRuntimeConfig.Answerability thresholds) {
        return Math.max(score(evidence.retrievalScore()), score(evidence.rerankScore()))
                >= thresholds.strongRecoveryRelevance();
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
