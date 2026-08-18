package com.healingplanet.ai.retrieval;

import com.healingplanet.ai.domain.QueryIntent;
import com.healingplanet.ai.domain.RagQuery;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

@Component
public class QueryRouter {
    private static final Set<String> STATE_TERMS = Set.of(
            "我的", "这盆", "当前", "现在", "今天", "实时", "过去一周", "过去7天", "过去24小时", "近24", "近7",
            "最近土壤", "最近温度", "最近湿度", "传感器", "土壤湿度", "温度适合", "环境异常",
            "下降这么快", "要不要浇水", "需要浇水"
    );
    private static final Set<String> COMMUNITY_TERMS = Set.of(
            "社区", "大家", "网友", "有人讨论", "经验", "帖子"
    );
    private static final Set<String> FORMAL_KNOWLEDGE_TERMS = Set.of(
            "官方", "正式", "指南", "规范", "标准"
    );
    private static final Set<String> PERSONAL_CONTEXT_TERMS = Set.of(
            "我的", "我这盆", "这盆", "当前", "现在", "今天", "实时", "传感器"
    );
    private static final Set<String> GENERIC_WATERING_STATE_TERMS = Set.of("要不要浇水", "需要浇水");
    private static final Pattern COMMUNITY_FOLLOW_UP =
            Pattern.compile("[？?。；;，,]\\s*社区");

    public RoutingDecision route(RagQuery query) {
        if (query.intent() == QueryIntent.DISEASE_DIAGNOSIS) {
            return new RoutingDecision(false, false, true, QueryIntent.DISEASE_DIAGNOSIS,
                    StateEvidenceNeed.STATE_DECISION);
        }
        if (query.intent() == QueryIntent.COMMUNITY_SEARCH) {
            return new RoutingDecision(false, true, false, QueryIntent.COMMUNITY_SEARCH, StateEvidenceNeed.NONE);
        }
        if (query.intent() == QueryIntent.PERSONAL_CARE) {
            return personalCareRoute(query.query());
        }
        if (query.intent() == QueryIntent.GENERAL_CARE) {
            return new RoutingDecision(true, false, false, QueryIntent.GENERAL_CARE, StateEvidenceNeed.NONE);
        }

        String text = query.query().toLowerCase(Locale.ROOT);
        boolean personalContext = PERSONAL_CONTEXT_TERMS.stream().anyMatch(text::contains);
        boolean state = STATE_TERMS.stream().anyMatch(term -> !GENERIC_WATERING_STATE_TERMS.contains(term)
                && text.contains(term));
        state = state || personalContext && GENERIC_WATERING_STATE_TERMS.stream().anyMatch(text::contains);
        boolean community = COMMUNITY_TERMS.stream().anyMatch(text::contains);
        if (community) {
            boolean mixed = FORMAL_KNOWLEDGE_TERMS.stream().anyMatch(text::contains)
                    || COMMUNITY_FOLLOW_UP.matcher(text).find();
            return new RoutingDecision(mixed, true, false, QueryIntent.COMMUNITY_SEARCH, StateEvidenceNeed.NONE);
        }
        if (state) return personalCareRoute(text);
        return new RoutingDecision(true, false, false, QueryIntent.GENERAL_CARE, StateEvidenceNeed.NONE);
    }

    private RoutingDecision personalCareRoute(String query) {
        String text = query == null ? "" : query.toLowerCase(Locale.ROOT);
        if (text.contains("过去24小时") || text.contains("趋势") || text.contains("近24")) {
            return new RoutingDecision(false, false, true, QueryIntent.PERSONAL_CARE,
                    StateEvidenceNeed.STATE_FACT_HISTORY);
        }
        if (text.contains("实时数据") || text.contains("还能当") || text.contains("太旧")
                || text.contains("数据时效")) {
            return new RoutingDecision(false, false, true, QueryIntent.PERSONAL_CARE,
                    StateEvidenceNeed.STATE_FRESHNESS);
        }
        boolean wateringDecision = text.contains("需要浇水") || text.contains("要不要浇水")
                || text.contains("要不要补水") || text.contains("可以马上浇水");
        if (wateringDecision) {
            boolean history = text.contains("现在需要浇水") && !text.contains("太旧");
            return new RoutingDecision(true, false, true, QueryIntent.PERSONAL_CARE,
                    history ? StateEvidenceNeed.STATE_DECISION_WITH_HISTORY : StateEvidenceNeed.STATE_DECISION);
        }
        return new RoutingDecision(false, false, true, QueryIntent.PERSONAL_CARE,
                StateEvidenceNeed.STATE_FACT_CURRENT);
    }

    public enum StateEvidenceNeed {
        NONE,
        STATE_FACT_CURRENT,
        STATE_FACT_HISTORY,
        STATE_FRESHNESS,
        STATE_DECISION,
        STATE_DECISION_WITH_HISTORY
    }

    public record RoutingDecision(boolean knowledge, boolean community, boolean state, QueryIntent intent,
                                  StateEvidenceNeed stateEvidenceNeed) {
        public RoutingDecision(boolean knowledge, boolean community, boolean state, QueryIntent intent) {
            this(knowledge, community, state, intent,
                    state ? StateEvidenceNeed.STATE_DECISION : StateEvidenceNeed.NONE);
        }
    }
}
