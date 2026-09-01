package com.healingplanet.ai.retrieval;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Read-only source of high-churn community ranking features; Qdrant remains responsible for semantic retrieval. */
@Repository
class CommunityRankingFeatureRepository {
    private final JdbcClient jdbcClient;

    CommunityRankingFeatureRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    Map<String, CommunityRankingFeatures> findByPostIds(Collection<String> postIds) {
        if (postIds == null || postIds.isEmpty()) return Map.of();
        String placeholders = postIds.stream().map(ignored -> "?").collect(Collectors.joining(", "));
        List<CommunityRankingFeatures> rows = jdbcClient.sql("""
                select id, likes, collects, comments, view, essence
                from post
                where (status = 1 or status is null) and id in (""" + placeholders + ")")
                .params(List.copyOf(postIds))
                .query((rs, rowNum) -> new CommunityRankingFeatures(rs.getString("id"),
                        valueOrZero(rs.getObject("likes", Integer.class)),
                        valueOrZero(rs.getObject("collects", Integer.class)),
                        valueOrZero(rs.getObject("comments", Integer.class)),
                        valueOrZero(rs.getObject("view", Integer.class)),
                        Boolean.TRUE.equals(rs.getObject("essence", Boolean.class))))
                .list();
        Map<String, CommunityRankingFeatures> result = new LinkedHashMap<>();
        rows.forEach(row -> result.put(row.postId(), row));
        return Map.copyOf(result);
    }

    private static int valueOrZero(Integer value) {
        return value == null ? 0 : value;
    }

    record CommunityRankingFeatures(String postId, int likes, int collects, int comments, int views, boolean essence) {
    }
}
