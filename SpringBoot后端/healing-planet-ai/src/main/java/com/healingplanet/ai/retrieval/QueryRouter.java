package com.healingplanet.ai.retrieval;

import com.healingplanet.ai.domain.QueryIntent;
import com.healingplanet.ai.domain.RagQuery;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

@Component
public class QueryRouter {
    private static final Set<String> STATE_TERMS = Set.of(
            "我的", "我这盆", "这盆", "这株", "这棵", "家里的", "过去一周", "过去7天", "过去24小时", "近24", "近7",
            "最近土壤", "最近温度", "最近湿度", "传感器", "土壤湿度", "温度适合", "环境异常",
            "下降这么快", "要不要浇水", "需要浇水", "状态异常", "有异常吗", "异常吗", "需要处理吗"
    );
    private static final Set<String> COMMUNITY_TERMS = Set.of(
            "社区", "大家", "网友", "花友", "有人讨论", "经验", "帖子"
    );
    private static final Set<String> FORMAL_KNOWLEDGE_TERMS = Set.of(
            "官方", "正式", "指南", "规范", "标准"
    );
    private static final Set<String> PERSONAL_CONTEXT_TERMS = Set.of(
            "我的", "我这盆", "这盆", "这株", "这棵", "家里的", "传感器"
    );
    private static final Set<String> CURRENT_STATE_PHRASES = Set.of(
            "当前状态", "当前土壤", "当前湿度", "当前温度", "当前读数", "当前数据"
    );
    private static final Set<String> HISTORY_TERMS = Set.of(
            "过去24小时", "过去一周", "过去7天", "近24", "近7", "趋势", "变化", "波动"
    );
    private static final Set<String> FRESHNESS_TERMS = Set.of(
            "实时数据", "还能当", "太旧", "数据时效"
    );
    private static final Set<String> WATERING_DECISION_TERMS = Set.of(
            "需要浇水", "要不要浇水", "要不要补水", "可以马上浇水"
    );
    private static final Set<String> STATE_DECISION_TERMS = Set.of(
            "状态异常", "有异常吗", "异常吗", "需要处理吗"
    );
    private static final Set<String> PLANT_DOMAIN_TERMS = Set.of(
            "植物", "绿植", "盆栽", "花卉", "花盆", "花草", "植株", "园艺", "种植", "栽培", "多肉",
            "养花", "养植物", "养绿植", "盆土", "根系", "叶片", "叶子", "浇水", "补水", "施肥", "肥料",
            "光照", "阳光", "温度", "湿度", "土壤", "修剪", "养护", "黄叶", "发黄", "枯黄", "耐阴",
            "喜阴", "弱光", "强光", "直射", "状态"
    );
    private static final Set<String> GENERIC_WATERING_STATE_TERMS = Set.of("要不要浇水", "需要浇水");
    private static final Pattern COMMUNITY_FOLLOW_UP =
            Pattern.compile("[？?。；;，,]\\s*社区");
    private static final Pattern COMMUNITY_NEGATION_SCOPE = Pattern.compile(
            "(?:不要|别|无需|不用|不参考|不采用|不混入|排除|去掉|剔除|避免)"
                    + "[^，。！？?;；]{0,16}$");
    private static final Pattern KNOWLEDGE_NEGATION_SCOPE = Pattern.compile(
            "(?:不要|别|无需|不用|不需要|不看|不参考|排除|去掉|剔除|避免)"
                    + "[^，。！？?;；]{0,16}$");
    private static final Pattern CLAUSE_SPLITTER = Pattern.compile("[？?。！!；;，,]+");
    private static final Pattern COMMUNITY_ONLY_PREFERENCE = Pattern.compile(
            "(?<!不)(?:只|仅)(?:想)?(?:要|看|看看|参考|采用|听|了解)?"
                    + "[^，。！？?;；]{0,8}(?:社区|大家|网友|花友|经验|帖子)");
    private static final Pattern KNOWLEDGE_ONLY_PREFERENCE = Pattern.compile(
            "(?<!不)(?:只|仅)(?:想)?(?:要|看|看看|给|参考|采用|了解)?"
                    + "[^，。！？?;；]{0,8}(?:官方|正式|指南|规范|标准)");
    private static final Pattern GENERIC_PLANT_QUERY = Pattern.compile(
            "^(?:请问|想问下|我想问|请|帮我)?(?:"
                    + "(?:什么|哪种|哪些|哪类|有哪些).*?(?:植物|绿植|盆栽|花卉)|"
                    + "(?:这种)?(?:植物|绿植|盆栽|花卉)(?:有哪些|推荐|比较好|的|叶|怎么|如何|适合|需要)|"
                    + "(?:适合|推荐).*(?:宿舍|室内|办公室|卧室|家里).*(?:植物|绿植|盆栽|花卉)|"
                    + "(?:宿舍|室内|办公室|卧室|家里).*(?:植物|绿植|盆栽|花卉)(?:有哪些|推荐|比较好))");
    private static final Pattern GENERIC_CARE_CONCEPT_QUERY = Pattern.compile(
            ".*(?:耐阴|喜阴|弱光|强光|直射|光照|浇水|补水|状态|异常).*(?:等于|区别|一样|相同|是什么意思|什么叫).*");
    private static final Pattern GENERIC_COMMUNITY_QUERY = Pattern.compile(
            "^(?:请问|想问下|我想问|请|帮我)?"
                    + "(?:(?:社区|网友|花友|大家|帖子|有人讨论)(?:里|中)?)*"
                    + "(?:最近|近期|现在|当前)?(?:有)?(?:哪些|什么|有什么|有哪些|哪类|推荐|热门|比较热门).*");
    private static final Pattern PLANT_CARE_ACTION_QUERY = Pattern.compile(".*(?:怎么|如何)养(?!生).*");
    private static final Pattern KNOWLEDGE_CARE_QUERY = Pattern.compile(
            ".*(?:怎么养|如何养|多久|频率|要求|范围|适合什么|需要多少|多少|怎么处理|如何处理|能否|是否|是什么|"
                    + "光照|阳光|晒太阳|浇水|补水|温度|湿度|施肥|肥料|土壤|盆土|介质|黄叶|枯黄|修剪|"
                    + "耐阴|喜阴|弱光|强光|直射|养护).*");

    public RoutingDecision route(RagQuery query) {
        String text = query.query() == null ? "" : query.query().toLowerCase(Locale.ROOT);
        RoutingDecision decision;
        if (query.intent() == QueryIntent.DISEASE_DIAGNOSIS) {
            decision = new RoutingDecision(false, false, true, QueryIntent.DISEASE_DIAGNOSIS,
                    StateEvidenceNeed.STATE_DECISION);
        } else if (query.intent() == QueryIntent.PERSONAL_CARE) {
            decision = personalCareRoute(text);
        } else if (query.intent() == QueryIntent.GENERAL_CARE) {
            decision = new RoutingDecision(true, false, false, QueryIntent.GENERAL_CARE, StateEvidenceNeed.NONE);
        } else {
            boolean personalContext = hasPersonalContext(text);
            boolean state = STATE_TERMS.stream().anyMatch(term -> !GENERIC_WATERING_STATE_TERMS.contains(term)
                    && text.contains(term));
            state = state || personalContext && GENERIC_WATERING_STATE_TERMS.stream().anyMatch(text::contains);
            state = state || CURRENT_STATE_PHRASES.stream().anyMatch(text::contains);
            SourcePlan sourcePlan = sourcePlan(text, query.intent() == QueryIntent.COMMUNITY_SEARCH);
            if (query.intent() == QueryIntent.COMMUNITY_SEARCH) {
                decision = new RoutingDecision(sourcePlan.includeKnowledge(), sourcePlan.includeCommunity(), false,
                        QueryIntent.COMMUNITY_SEARCH, StateEvidenceNeed.NONE);
            } else if (sourcePlan.includeCommunity()) {
                decision = new RoutingDecision(sourcePlan.includeKnowledge(), true, false,
                        QueryIntent.COMMUNITY_SEARCH, StateEvidenceNeed.NONE);
            } else if (state) {
                decision = personalCareRoute(text);
            } else {
                decision = new RoutingDecision(true, false, false, QueryIntent.GENERAL_CARE,
                        StateEvidenceNeed.NONE);
            }
        }
        return withEntityPolicy(decision, text, query.intent() != null);
    }

    private RoutingDecision withEntityPolicy(RoutingDecision decision, String text, boolean explicitIntent) {
        String compactText = text.replaceAll("\\s+", "");
        boolean genericPlant = GENERIC_PLANT_QUERY.matcher(compactText).find()
                || GENERIC_CARE_CONCEPT_QUERY.matcher(compactText).matches();
        boolean genericCommunity = decision.intent() == QueryIntent.COMMUNITY_SEARCH
                && GENERIC_COMMUNITY_QUERY.matcher(compactText).matches();
        boolean explicitPlantIntent = explicitIntent && decision.intent() != QueryIntent.COMMUNITY_SEARCH;
        boolean plantDomain = explicitPlantIntent || genericPlant || isPlantDomainQuery(compactText);
        boolean generic = genericPlant || genericCommunity;
        EntityRequirement entityRequirement = !plantDomain ? EntityRequirement.NONE
                : generic ? EntityRequirement.OPTIONAL : EntityRequirement.REQUIRED;
        return new RoutingDecision(decision.knowledge(), decision.community(), decision.state(), decision.intent(),
                decision.stateEvidenceNeed(), plantDomain ? QueryDomain.PLANT : QueryDomain.OUT_OF_DOMAIN,
                entityRequirement);
    }

    private boolean isPlantDomainQuery(String text) {
        return PLANT_DOMAIN_TERMS.stream().anyMatch(text::contains)
                || text.contains("浇") || text.contains("补") || text.contains("晒") || text.contains("太阳")
                || PLANT_CARE_ACTION_QUERY.matcher(text).matches();
    }

    private boolean hasPersonalContext(String text) {
        return PERSONAL_CONTEXT_TERMS.stream().anyMatch(text::contains)
                || CURRENT_STATE_PHRASES.stream().anyMatch(text::contains);
    }

    private boolean hasPositiveCommunityIntent(String text) {
        return COMMUNITY_TERMS.stream().anyMatch(term -> text.contains(term) && !isNegatedCommunityTerm(text, term));
    }

    private SourcePlan sourcePlan(String text, boolean communityByIntent) {
        boolean communityMentioned = COMMUNITY_TERMS.stream().anyMatch(text::contains);
        boolean positiveCommunityMentioned = hasPositiveCommunityIntent(text);
        boolean communityExcluded = communityMentioned && !positiveCommunityMentioned;
        boolean knowledgeExcluded = isKnowledgeExcluded(text);
        boolean communityOnly = hasCommunityOnlyPreference(text);
        boolean knowledgeOnly = hasKnowledgeOnlyPreference(text);

        if (communityOnly) {
            return new SourcePlan(false, !communityExcluded);
        }
        if (knowledgeOnly) {
            return new SourcePlan(!knowledgeExcluded, false);
        }

        boolean knowledgeRequested = false;
        boolean communityRequested = false;
        for (String clause : splitClauses(text)) {
            if (!communityExcluded && hasPositiveCommunityIntent(clause)) {
                communityRequested = true;
            }
            if (!knowledgeExcluded && hasPositiveKnowledgeClause(clause)) {
                knowledgeRequested = true;
            }
        }
        if (!communityExcluded && COMMUNITY_FOLLOW_UP.matcher(text).find()) {
            communityRequested = true;
        }
        if (communityByIntent && !communityExcluded) {
            communityRequested = true;
        }
        return new SourcePlan(knowledgeRequested, communityRequested);
    }

    private List<String> splitClauses(String text) {
        return CLAUSE_SPLITTER.splitAsStream(text)
                .map(String::trim)
                .filter(clause -> !clause.isBlank())
                .toList();
    }

    private boolean hasPositiveKnowledgeClause(String clause) {
        String compactClause = clause.replaceAll("\\s+", "");
        if (compactClause.isBlank()) return false;
        if (hasPositiveCommunityIntent(compactClause)
                && FORMAL_KNOWLEDGE_TERMS.stream().noneMatch(compactClause::contains)) {
            return false;
        }
        return FORMAL_KNOWLEDGE_TERMS.stream().anyMatch(compactClause::contains)
                || isPlantDomainQuery(compactClause) && KNOWLEDGE_CARE_QUERY.matcher(compactClause).matches();
    }

    private boolean isNegatedCommunityTerm(String text, String term) {
        int index = text.indexOf(term);
        while (index >= 0) {
            String prefix = text.substring(Math.max(0, index - 24), index);
            if (!COMMUNITY_NEGATION_SCOPE.matcher(prefix).find()) return false;
            index = text.indexOf(term, index + term.length());
        }
        return true;
    }

    private boolean isKnowledgeExcluded(String text) {
        return FORMAL_KNOWLEDGE_TERMS.stream()
                .anyMatch(term -> isNegatedTerm(text, term, KNOWLEDGE_NEGATION_SCOPE));
    }

    private boolean hasCommunityOnlyPreference(String text) {
        return COMMUNITY_ONLY_PREFERENCE.matcher(text).find();
    }

    private boolean hasKnowledgeOnlyPreference(String text) {
        return KNOWLEDGE_ONLY_PREFERENCE.matcher(text).find();
    }

    private boolean isNegatedTerm(String text, String term, Pattern scope) {
        int index = text.indexOf(term);
        while (index >= 0) {
            String prefix = text.substring(Math.max(0, index - 24), index);
            if (scope.matcher(prefix).find()) {
                return true;
            }
            index = text.indexOf(term, index + term.length());
        }
        return false;
    }

    private RoutingDecision personalCareRoute(String query) {
        String text = query == null ? "" : query.toLowerCase(Locale.ROOT);
        boolean historyRequested = HISTORY_TERMS.stream().anyMatch(text::contains)
                || text.contains("现在需要浇水");
        boolean freshnessCheck = FRESHNESS_TERMS.stream().anyMatch(text::contains);
        boolean wateringDecision = WATERING_DECISION_TERMS.stream().anyMatch(text::contains);
        boolean stateDecision = STATE_DECISION_TERMS.stream().anyMatch(text::contains);
        if (freshnessCheck) {
            return new RoutingDecision(false, false, true, QueryIntent.PERSONAL_CARE,
                    StateEvidenceNeed.STATE_FRESHNESS);
        }
        if (wateringDecision) {
            return new RoutingDecision(true, false, true, QueryIntent.PERSONAL_CARE,
                    historyRequested ? StateEvidenceNeed.STATE_DECISION_WITH_HISTORY : StateEvidenceNeed.STATE_DECISION);
        }
        if (stateDecision) {
            return new RoutingDecision(true, false, true, QueryIntent.PERSONAL_CARE,
                    StateEvidenceNeed.STATE_DECISION);
        }
        if (historyRequested) {
            return new RoutingDecision(false, false, true, QueryIntent.PERSONAL_CARE,
                    StateEvidenceNeed.STATE_FACT_HISTORY);
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

    public enum QueryDomain { PLANT, OUT_OF_DOMAIN }

    public enum EntityRequirement { NONE, OPTIONAL, REQUIRED }

    public record RoutingDecision(boolean knowledge, boolean community, boolean state, QueryIntent intent,
                                  StateEvidenceNeed stateEvidenceNeed, QueryDomain domain,
                                  EntityRequirement entityRequirement) {
        public RoutingDecision(boolean knowledge, boolean community, boolean state, QueryIntent intent,
                               StateEvidenceNeed stateEvidenceNeed) {
            this(knowledge, community, state, intent, stateEvidenceNeed, QueryDomain.PLANT,
                    EntityRequirement.REQUIRED);
        }

        public RoutingDecision(boolean knowledge, boolean community, boolean state, QueryIntent intent) {
            this(knowledge, community, state, intent,
                    state ? StateEvidenceNeed.STATE_DECISION : StateEvidenceNeed.NONE);
        }

        public boolean plantDomain() {
            return domain == QueryDomain.PLANT;
        }
    }

    private record SourcePlan(boolean includeKnowledge, boolean includeCommunity) { }
}
