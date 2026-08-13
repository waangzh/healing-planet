package com.healingplanet.ai.retrieval;

import com.healingplanet.ai.domain.QueryIntent;
import com.healingplanet.ai.domain.RagQuery;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Set;

@Component
public class QueryRouter {
    private static final Set<String> STATE_TERMS = Set.of(
            "我的", "这盆", "当前", "现在", "今天", "实时", "过去一周", "过去7天", "过去24小时", "近24", "近7",
            "最近土壤", "最近温度", "最近湿度", "传感器", "土壤湿度", "温度适合", "环境异常",
            "下降这么快", "要不要浇水", "需要浇水"
    );
    private static final Set<String> COMMUNITY_TERMS = Set.of(
            "社区", "大家", "网友", "有人讨论", "经验", "帖子", "怎么处理"
    );

    public RoutingDecision route(RagQuery query) {
        if (query.intent() == QueryIntent.DISEASE_DIAGNOSIS) {
            return new RoutingDecision(false, false, true, QueryIntent.DISEASE_DIAGNOSIS);
        }
        if (query.intent() == QueryIntent.COMMUNITY_SEARCH) {
            return new RoutingDecision(false, true, false, QueryIntent.COMMUNITY_SEARCH);
        }
        if (query.intent() == QueryIntent.PERSONAL_CARE) {
            return new RoutingDecision(true, false, true, QueryIntent.PERSONAL_CARE);
        }
        if (query.intent() == QueryIntent.GENERAL_CARE) {
            return new RoutingDecision(true, true, false, QueryIntent.GENERAL_CARE);
        }

        String text = query.query().toLowerCase(Locale.ROOT);
        boolean state = STATE_TERMS.stream().anyMatch(text::contains);
        boolean community = COMMUNITY_TERMS.stream().anyMatch(text::contains);
        if (community) return new RoutingDecision(true, true, false, QueryIntent.COMMUNITY_SEARCH);
        if (state) return new RoutingDecision(true, false, true, QueryIntent.PERSONAL_CARE);
        return new RoutingDecision(true, true, false, QueryIntent.GENERAL_CARE);
    }

    public record RoutingDecision(boolean knowledge, boolean community, boolean state, QueryIntent intent) { }
}
