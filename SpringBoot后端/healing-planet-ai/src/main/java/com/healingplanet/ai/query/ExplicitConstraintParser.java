package com.healingplanet.ai.query;

import com.healingplanet.ai.domain.RagQuery;
import com.healingplanet.ai.domain.QueryIntent;
import com.healingplanet.ai.retrieval.SourcePlan;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/** Parses only explicit source directives. Relevance predictions are intentionally excluded. */
@Component
public class ExplicitConstraintParser {
    private static final Pattern COMMUNITY_ONLY = Pattern.compile(
            "(?<!不)(?:只|仅)(?:想)?(?:看|参考|采用|听|了解)?[^，。！？?;；]{0,10}(?:社区|大家|网友|花友|帖子|经验)");
    private static final Pattern FORMAL_ONLY = Pattern.compile(
            "(?<!不)(?:只|仅)(?:想)?(?:看|参考|采用|了解|按)?[^，。！？?;；]{0,10}(?:官方|正式|指南|规范|标准)");
    private static final Pattern NEGATION = Pattern.compile("(?:不要|别|无需|不用|不需要|不看|不参考|不引用|排除|去掉|剔除|避免)");

    public RetrievalConstraints parse(RagQuery query) {
        String text = QueryLexicon.normalize(query.query());
        boolean communityMentioned = QueryLexicon.containsAny(text, QueryLexicon.COMMUNITY);
        boolean formalMentioned = QueryLexicon.containsAny(text, QueryLexicon.FORMAL);
        boolean communityOnly = COMMUNITY_ONLY.matcher(text).find();
        boolean formalOnly = FORMAL_ONLY.matcher(text).find();
        boolean communityForbidden = formalOnly || explicitlyNegated(text, QueryLexicon.COMMUNITY);
        boolean formalForbidden = communityOnly || explicitlyNegated(text, QueryLexicon.FORMAL);
        boolean stateForbidden = explicitlyNegated(text, java.util.Set.of("实时数据", "传感器", "状态数据"));
        boolean communityRequired = communityOnly || (communityMentioned && formalMentioned)
                || query.intent() == QueryIntent.COMMUNITY_SEARCH;

        SourcePlan.SourceRequirement knowledge = formalForbidden ? SourcePlan.SourceRequirement.FORBIDDEN
                : formalOnly || formalMentioned && communityMentioned ? SourcePlan.SourceRequirement.REQUIRED
                : SourcePlan.SourceRequirement.ALLOWED;
        SourcePlan.SourceRequirement community = communityForbidden ? SourcePlan.SourceRequirement.FORBIDDEN
                : communityRequired ? SourcePlan.SourceRequirement.REQUIRED
                : SourcePlan.SourceRequirement.ALLOWED;
        return new RetrievalConstraints(knowledge, community,
                stateForbidden ? SourcePlan.SourceRequirement.FORBIDDEN : SourcePlan.SourceRequirement.ALLOWED);
    }

    private boolean explicitlyNegated(String text, java.util.Set<String> terms) {
        for (String term : terms) {
            int index = text.indexOf(term);
            while (index >= 0) {
                String prefix = text.substring(Math.max(0, index - 18), index);
                int clauseBoundary = Math.max(Math.max(prefix.lastIndexOf('，'), prefix.lastIndexOf(',')),
                        Math.max(prefix.lastIndexOf('。'), prefix.lastIndexOf('；')));
                if (clauseBoundary >= 0) prefix = prefix.substring(clauseBoundary + 1);
                if (NEGATION.matcher(prefix).find()) return true;
                index = text.indexOf(term, index + term.length());
            }
        }
        return false;
    }
}
