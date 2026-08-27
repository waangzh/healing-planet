package com.healingplanet.ai.evaluation;

import com.healingplanet.ai.domain.EntityResolutionDiagnostics;
import com.healingplanet.ai.domain.Evidence;
import com.healingplanet.ai.domain.EvidenceType;
import com.healingplanet.ai.query.StateNeed;
import com.healingplanet.ai.retrieval.RetrievalRequest;
import org.springframework.stereotype.Component;

import java.util.List;

/** Makes safe outcomes from retrieved evidence rather than a pre-retrieval classifier. */
@Component
public class AnswerabilityEvaluator {
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
        if (!evidence.isEmpty()) return new Assessment(Answerability.ANSWERABLE, "evidence_selected");
        return request.analysis().plantDomainConfidence() < 0.3d
                ? new Assessment(Answerability.OUT_OF_SCOPE, "no_relevant_evidence_low_plant_hint")
                : new Assessment(Answerability.INSUFFICIENT_EVIDENCE, "no_selected_evidence");
    }

    public record Assessment(Answerability result, String reason) { }
}
