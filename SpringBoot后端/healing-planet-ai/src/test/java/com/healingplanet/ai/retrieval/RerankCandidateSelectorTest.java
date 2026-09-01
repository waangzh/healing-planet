package com.healingplanet.ai.retrieval;

import com.healingplanet.ai.domain.KnowledgeDocument;
import com.healingplanet.ai.domain.KnowledgeSource;
import com.healingplanet.ai.domain.QueryIntent;
import com.healingplanet.ai.domain.RagQuery;
import com.healingplanet.ai.query.QueryAnalysis;
import com.healingplanet.ai.query.RetrievalConstraints;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class RerankCandidateSelectorTest {

    private final RerankCandidateSelector selector = new RerankCandidateSelector();

    @Test
    void shouldReserveRerankAdmissionForRequiredCommunityOutsideFusionTopK() {
        SourcePlan sourcePlan = new SourcePlan(SourcePlan.SourceRequirement.ALLOWED,
                SourcePlan.SourceRequirement.REQUIRED, SourcePlan.SourceRequirement.FORBIDDEN);
        RetrievalQueryGroup group = new RetrievalQueryGroup("Q1", "绿萝社区经验", GroupRole.PRIMARY,
                Set.of(), Set.of(), SourceScope.from(sourcePlan), true);
        RagQuery query = RagQuery.of("绿萝的社区养护经验");
        RetrievalRequest request = new RetrievalRequest(query, new QueryAnalysis(QueryIntent.COMMUNITY_SEARCH,
                Set.of(), Set.of(), false, 0.9d), RetrievalConstraints.defaults(),
                new RetrievalPlan(sourcePlan, true, true, false, Set.of(), Set.of(), query.query(), List.of(group)),
                null, query.query());
        List<LogicalEvidenceCandidate> candidates = new ArrayList<>();
        for (int index = 1; index <= 20; index++) {
            candidates.add(candidate("plant-" + index, KnowledgeSource.PLANT));
        }
        candidates.add(candidate("community", KnowledgeSource.COMMUNITY));

        List<LogicalEvidenceCandidate> selected = selector.select(request, candidates, 2);

        assertThat(selected).extracting(LogicalEvidenceCandidate::logicalEvidenceId)
                .containsExactly("COMMUNITY:community", "PLANT:plant-1");
    }

    private LogicalEvidenceCandidate candidate(String id, KnowledgeSource source) {
        KnowledgeDocument document = new KnowledgeDocument(id, source, id, id, id, "", "绿萝",
                source == KnowledgeSource.PLANT ? "WATERING" : "COMMUNITY_EXPERIENCE", List.of(), 0.8,
                false, 0, 0, 0, 0, Instant.EPOCH, Map.of());
        return new LogicalEvidenceCandidate(source.name() + ":" + id, document,
                List.of(RetrievalFragmentHit.dense(document, 1, 0.9)), 1, null, 0.9, null, 0.1, Set.of("Q1"));
    }
}
