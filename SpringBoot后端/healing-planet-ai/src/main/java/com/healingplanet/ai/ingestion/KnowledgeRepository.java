package com.healingplanet.ai.ingestion;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public class KnowledgeRepository {

    private final JdbcClient jdbcClient;

    public KnowledgeRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public List<PlantRow> findPlants() {
        return jdbcClient.sql("""
                select p.id, p.scientific_name, p.common_name,
                       g.light_requirements, g.watering_frequency,
                       g.temperature_preference, g.humidity_preference,
                       g.fertilizing_tips, g.detail_advice
                from plants p
                left join plant_care_guides g on p.id = g.plant_id
                """).query((rs, rowNum) -> new PlantRow(
                rs.getString("id"), rs.getString("scientific_name"), rs.getString("common_name"),
                rs.getString("light_requirements"), rs.getString("watering_frequency"),
                rs.getString("temperature_preference"), rs.getString("humidity_preference"),
                rs.getString("fertilizing_tips"), rs.getString("detail_advice")
        )).list();
    }

    public List<PostRow> findPublishedPosts() {
        return jdbcClient.sql("""
                select p.id, p.title, p.content, p.likes, p.collects, p.comments, p.view,
                       p.essence, p.create_time, group_concat(distinct t.name separator ',') tags
                from post p
                left join post_tag pt on p.id = pt.post_id
                left join tag t on pt.tag_id = t.id
                where (p.status = 1 or p.status is null)
                group by p.id, p.title, p.content, p.likes, p.collects, p.comments,
                         p.view, p.essence, p.create_time
                """).query(this::mapPost).list();
    }

    public PostRow findPublishedPost(String postId) {
        return jdbcClient.sql("""
                select p.id, p.title, p.content, p.likes, p.collects, p.comments, p.view,
                       p.essence, p.create_time, group_concat(distinct t.name separator ',') tags
                from post p
                left join post_tag pt on p.id = pt.post_id
                left join tag t on pt.tag_id = t.id
                where p.id = :postId and (p.status = 1 or p.status is null)
                group by p.id, p.title, p.content, p.likes, p.collects, p.comments,
                         p.view, p.essence, p.create_time
                """).param("postId", postId).query(this::mapPost).optional()
                .orElse(null);
    }

    private PostRow mapPost(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        var timestamp = rs.getTimestamp("create_time");
        return new PostRow(
                rs.getString("id"), rs.getString("title"), rs.getString("content"),
                valueOrZero(rs.getObject("likes", Integer.class)),
                valueOrZero(rs.getObject("collects", Integer.class)),
                valueOrZero(rs.getObject("comments", Integer.class)),
                valueOrZero(rs.getObject("view", Integer.class)),
                Boolean.TRUE.equals(rs.getObject("essence", Boolean.class)),
                timestamp == null ? null : timestamp.toInstant(), rs.getString("tags")
        );
    }

    private int valueOrZero(Integer value) {
        return value == null ? 0 : value;
    }

    public record PlantRow(String id, String scientificName, String commonName,
                           String lightRequirements, String wateringFrequency,
                           String temperaturePreference, String humidityPreference,
                           String fertilizingTips, String detailAdvice) {
    }

    public record PostRow(String id, String title, String content, int likes, int collects,
                          int comments, int views, boolean essence, Instant createdAt, String tags) {
    }
}
