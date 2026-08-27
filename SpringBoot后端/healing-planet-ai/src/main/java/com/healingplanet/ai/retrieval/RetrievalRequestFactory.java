package com.healingplanet.ai.retrieval;

import com.healingplanet.ai.domain.RagQuery;
import com.healingplanet.ai.query.ExplicitConstraintParser;
import com.healingplanet.ai.query.QueryAnalysis;
import com.healingplanet.ai.query.QueryAnalyzer;
import com.healingplanet.ai.query.RetrievalConstraints;
import org.springframework.stereotype.Component;

/** Builds the single immutable analysis/plan request shared by all retrievers. */
@Component
public class RetrievalRequestFactory {
    private final QueryAnalyzer queryAnalyzer;
    private final ExplicitConstraintParser constraintParser;
    private final PlantEntityResolver entityResolver;
    private final RetrievalPlanner retrievalPlanner;

    public RetrievalRequestFactory(QueryAnalyzer queryAnalyzer, ExplicitConstraintParser constraintParser,
                                   PlantEntityResolver entityResolver, RetrievalPlanner retrievalPlanner) {
        this.queryAnalyzer = queryAnalyzer;
        this.constraintParser = constraintParser;
        this.entityResolver = entityResolver;
        this.retrievalPlanner = retrievalPlanner;
    }

    public RetrievalRequest create(RagQuery query) {
        QueryAnalysis analysis = queryAnalyzer.analyze(query);
        RetrievalConstraints constraints = constraintParser.parse(query);
        PlantEntityResolver.Resolution entity = entityResolver.resolveQuery(query);
        RetrievalPlan plan = retrievalPlanner.plan(query, analysis, constraints, entity);
        return new RetrievalRequest(query, analysis, constraints, plan, entity, plan.searchQuery());
    }
}
