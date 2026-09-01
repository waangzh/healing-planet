package com.healingplanet.ai.ingestion;

import com.healingplanet.ai.config.RagProperties;
import com.healingplanet.ai.domain.KnowledgeDocument;
import com.healingplanet.ai.domain.KnowledgeSource;
import io.qdrant.client.PointIdFactory;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.ValueFactory;
import io.qdrant.client.grpc.JsonWithInt.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/** Qdrant's payload API keeps vectors intact, unlike VectorStore.add which invokes the embedding model. */
@Component
final class QdrantPayloadUpdater implements VectorPayloadUpdater {
    private static final Duration WRITE_TIMEOUT = Duration.ofSeconds(10);

    private final QdrantClient client;
    private final RagProperties properties;

    QdrantPayloadUpdater(QdrantClient client, RagProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    @Override
    public void overwritePayloads(KnowledgeSource source, List<KnowledgeDocument> documents) {
        if (documents.isEmpty()) return;
        String collection = collection(source);
        List<Future<?>> updates = new ArrayList<>(documents.size());
        for (KnowledgeDocument document : documents) {
            updates.add(client.overwritePayloadAsync(collection, payload(document),
                    PointIdFactory.id(UUID.fromString(document.id())), true, null, WRITE_TIMEOUT));
        }
        try {
            for (Future<?> update : updates) {
                update.get(WRITE_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("更新 Qdrant payload 被中断", exception);
        } catch (Exception exception) {
            throw new IllegalStateException("更新 Qdrant payload 失败", exception);
        }
    }

    private String collection(KnowledgeSource source) {
        return switch (source) {
            case PLANT -> properties.getQdrant().getPlantCollection();
            case PLANT_ENTITY -> properties.getQdrant().getPlantEntityCollection();
            case COMMUNITY -> properties.getQdrant().getCommunityCollection();
            case DISEASE -> properties.getQdrant().getDiseaseCollection();
        };
    }

    private Map<String, Value> payload(KnowledgeDocument document) {
        return document.vectorPayloadMetadata().entrySet().stream()
                .collect(java.util.stream.Collectors.toMap(Map.Entry::getKey,
                        entry -> value(entry.getValue()), (left, right) -> right, java.util.LinkedHashMap::new));
    }

    private Value value(Object value) {
        if (value instanceof Boolean booleanValue) return ValueFactory.value(booleanValue);
        if (value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long) {
            return ValueFactory.value(((Number) value).longValue());
        }
        if (value instanceof Number numberValue) return ValueFactory.value(numberValue.doubleValue());
        return ValueFactory.value(value == null ? "" : value.toString());
    }
}
