package com.healingplanet.ai.config;

import io.qdrant.client.QdrantClient;
import io.qdrant.client.QdrantGrpcClient;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.qdrant.QdrantVectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class AiConfiguration {

    @Bean(destroyMethod = "close")
    QdrantClient qdrantClient(RagProperties properties) {
        var qdrant = properties.getQdrant();
        var builder = QdrantGrpcClient.newBuilder(qdrant.getHost(), qdrant.getPort(), qdrant.isTls());
        if (qdrant.getApiKey() != null && !qdrant.getApiKey().isBlank()) {
            builder.withApiKey(qdrant.getApiKey());
        }
        return new QdrantClient(builder.build());
    }

    @Bean("plantVectorStore")
    VectorStore plantVectorStore(QdrantClient client, EmbeddingModel embeddingModel, RagProperties properties) {
        return vectorStore(client, embeddingModel, properties.getQdrant().getPlantCollection(), properties);
    }

    @Bean("communityVectorStore")
    VectorStore communityVectorStore(QdrantClient client, EmbeddingModel embeddingModel, RagProperties properties) {
        return vectorStore(client, embeddingModel, properties.getQdrant().getCommunityCollection(), properties);
    }

    private VectorStore vectorStore(QdrantClient client, EmbeddingModel embeddingModel,
                                    String collection, RagProperties properties) {
        return QdrantVectorStore.builder(client, embeddingModel)
                .collectionName(collection)
                .initializeSchema(properties.getQdrant().isInitializeSchema())
                .build();
    }

    @Bean
    ChatClient chatClient(ChatModel chatModel) {
        return ChatClient.create(chatModel);
    }

    @Bean("rerankerRestClient")
    RestClient rerankerRestClient(RagProperties properties) {
        RestClient.Builder builder = RestClient.builder().baseUrl(properties.getReranker().getBaseUrl());
        if (properties.getReranker().getApiKey() != null && !properties.getReranker().getApiKey().isBlank()) {
            builder.defaultHeader("Authorization", "Bearer " + properties.getReranker().getApiKey());
        }
        return builder.build();
    }
}
