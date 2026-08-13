package com.healingplanet.ai.retrieval;

import com.healingplanet.ai.domain.QueryIntent;
import com.healingplanet.ai.domain.RagQuery;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class QueryRouterTest {
    private final QueryRouter router = new QueryRouter();

    @Test
    void shouldRoutePersonalQuestionToKnowledgeAndState() {
        var result = router.route(RagQuery.of("我的绿萝今天要不要浇水？"));

        assertThat(result.knowledge()).isTrue();
        assertThat(result.state()).isTrue();
        assertThat(result.community()).isFalse();
        assertThat(result.intent()).isEqualTo(QueryIntent.PERSONAL_CARE);
    }

    @Test
    void explicitIntentShouldTakePriority() {
        var query = new RagQuery("最近大家怎么养绿萝", null, null, null,
                QueryIntent.COMMUNITY_SEARCH, List.of(), Map.of());

        assertThat(router.route(query).intent()).isEqualTo(QueryIntent.COMMUNITY_SEARCH);
    }

    @Test
    void communityWordingShouldNotBeMistakenForRecentPlantState() {
        var result = router.route(RagQuery.of("最近大家怎么养绿萝？"));

        assertThat(result.intent()).isEqualTo(QueryIntent.COMMUNITY_SEARCH);
        assertThat(result.state()).isFalse();
    }
}
