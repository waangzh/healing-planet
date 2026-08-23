package com.healingplanet.ai.ingestion;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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

    public List<PlantRow> findPlantsAfter(String lastId, int limit) {
        return jdbcClient.sql("""
                select p.id, p.scientific_name, p.common_name,
                       g.light_requirements, g.watering_frequency,
                       g.temperature_preference, g.humidity_preference,
                       g.fertilizing_tips, g.detail_advice
                from plants p
                left join plant_care_guides g on p.id = g.plant_id
                where p.id > :lastId
                order by p.id
                limit :limit
                """).param("lastId", lastId).param("limit", limit).query((rs, rowNum) -> new PlantRow(
                rs.getString("id"), rs.getString("scientific_name"), rs.getString("common_name"),
                rs.getString("light_requirements"), rs.getString("watering_frequency"),
                rs.getString("temperature_preference"), rs.getString("humidity_preference"),
                rs.getString("fertilizing_tips"), rs.getString("detail_advice")
        )).list();
    }

    public List<PlantEntityRow> findPlantEntities() {
        Map<String, List<String>> aliasesByPlantId = jdbcClient.sql("""
                select plant_id, alias
                from plant_aliases
                where enabled = true
                order by plant_id, id
                """).query((rs, rowNum) -> new PlantAliasRow(
                rs.getString("plant_id"), rs.getString("alias")
        )).list().stream().collect(Collectors.groupingBy(
                PlantAliasRow::plantId, LinkedHashMap::new,
                Collectors.mapping(PlantAliasRow::alias, Collectors.toList())
        ));
        return jdbcClient.sql("""
                select p.id, p.scientific_name, p.common_name
                from plants p
                order by p.id
                """).query((rs, rowNum) -> new PlantEntityRow(
                rs.getString("id"), rs.getString("scientific_name"), rs.getString("common_name"),
                aliasesByPlantId.getOrDefault(rs.getString("id"), List.of())
        )).list();
    }

    public List<PlantEntityRow> findPlantEntitiesAfter(String lastId, int limit) {
        return jdbcClient.sql("""
                select p.id, p.scientific_name, p.common_name,
                       group_concat(a.alias order by a.id separator '|||') aliases
                from plants p
                left join plant_aliases a on a.plant_id = p.id and a.enabled = true
                where p.id > :lastId
                group by p.id, p.scientific_name, p.common_name
                order by p.id
                limit :limit
                """).param("lastId", lastId).param("limit", limit).query((rs, rowNum) -> new PlantEntityRow(
                rs.getString("id"), rs.getString("scientific_name"), rs.getString("common_name"),
                splitAliases(rs.getString("aliases"))
        )).list();
    }

    public List<PostRow> findPublishedPosts() {
        return jdbcClient.sql("""
                select p.id, p.title, p.content, p.likes, p.collects, p.comments, p.view,
                       p.essence, p.create_time, p.modify_time, group_concat(distinct t.name separator ',') tags
                from post p
                left join post_tag pt on p.id = pt.post_id
                left join tag t on pt.tag_id = t.id
                where (p.status = 1 or p.status is null)
                group by p.id, p.title, p.content, p.likes, p.collects, p.comments,
                         p.view, p.essence, p.create_time, p.modify_time
                """).query(this::mapPost).list();
    }

    public List<PostRow> findPublishedPostsAfter(String lastId, int limit) {
        return jdbcClient.sql("""
                select p.id, p.title, p.content, p.likes, p.collects, p.comments, p.view,
                       p.essence, p.create_time, p.modify_time, group_concat(distinct t.name separator ',') tags
                from post p
                left join post_tag pt on p.id = pt.post_id
                left join tag t on pt.tag_id = t.id
                where (p.status = 1 or p.status is null) and p.id > :lastId
                group by p.id, p.title, p.content, p.likes, p.collects, p.comments,
                         p.view, p.essence, p.create_time, p.modify_time
                order by p.id
                limit :limit
                """).param("lastId", lastId).param("limit", limit).query(this::mapPost).list();
    }

    public PostRow findPublishedPost(String postId) {
        return jdbcClient.sql("""
                select p.id, p.title, p.content, p.likes, p.collects, p.comments, p.view,
                       p.essence, p.create_time, p.modify_time, group_concat(distinct t.name separator ',') tags
                from post p
                left join post_tag pt on p.id = pt.post_id
                left join tag t on pt.tag_id = t.id
                where p.id = :postId and (p.status = 1 or p.status is null)
                group by p.id, p.title, p.content, p.likes, p.collects, p.comments,
                         p.view, p.essence, p.create_time, p.modify_time
                """).param("postId", postId).query(this::mapPost).optional()
                .orElse(null);
    }

    private PostRow mapPost(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        var timestamp = rs.getTimestamp("create_time");
        var modifiedTimestamp = rs.getTimestamp("modify_time");
        return new PostRow(
                rs.getString("id"), rs.getString("title"), rs.getString("content"),
                valueOrZero(rs.getObject("likes", Integer.class)),
                valueOrZero(rs.getObject("collects", Integer.class)),
                valueOrZero(rs.getObject("comments", Integer.class)),
                valueOrZero(rs.getObject("view", Integer.class)),
                Boolean.TRUE.equals(rs.getObject("essence", Boolean.class)),
                timestamp == null ? null : timestamp.toInstant(),
                modifiedTimestamp == null ? null : modifiedTimestamp.toInstant(), rs.getString("tags")
        );
    }

    private int valueOrZero(Integer value) {
        return value == null ? 0 : value;
    }

    private List<String> splitAliases(String aliases) {
        if (aliases == null || aliases.isBlank()) {
            return List.of();
        }
        return java.util.Arrays.stream(aliases.split("\\Q|||\\E"))
                .filter(alias -> !alias.isBlank()).toList();
    }

    public record PlantRow(String id, String scientificName, String commonName,
                           String lightRequirements, String wateringFrequency,
                           String temperaturePreference, String humidityPreference,
                           String fertilizingTips, String detailAdvice) {
    }

    public record PlantEntityRow(String id, String scientificName, String commonName, List<String> aliases) {
        public PlantEntityRow {
            aliases = aliases == null ? List.of() : List.copyOf(aliases);
        }

        public PlantEntityRow(String id, String scientificName, String commonName) {
            this(id, scientificName, commonName, List.of());
        }
    }

    private record PlantAliasRow(String plantId, String alias) {
    }

    public record PostRow(String id, String title, String content, int likes, int collects,
                          int comments, int views, boolean essence, Instant createdAt, Instant updatedAt, String tags) {
        public PostRow(String id, String title, String content, int likes, int collects,
                       int comments, int views, boolean essence, Instant createdAt, String tags) {
            this(id, title, content, likes, collects, comments, views, essence, createdAt, createdAt, tags);
        }
    }
}
