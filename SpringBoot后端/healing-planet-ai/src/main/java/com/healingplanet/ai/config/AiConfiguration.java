package com.healingplanet.ai.config;

import com.knuddels.jtokkit.api.EncodingType;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.QdrantGrpcClient;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.BatchingStrategy;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.TokenCountBatchingStrategy;
import org.springframework.ai.model.openai.autoconfigure.OpenAIAutoConfigurationUtil;
import org.springframework.ai.model.openai.autoconfigure.OpenAiChatProperties;
import org.springframework.ai.model.openai.autoconfigure.OpenAiConnectionProperties;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.qdrant.QdrantVectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.retry.support.RetryTemplate;
import com.healingplanet.ai.retrieval.PlantEntityDisambiguator;

import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Clock;
import java.util.Map;

@Configuration
public class AiConfiguration {

    @Bean("ragClock")
    @Profile("!eval")
    Clock ragClock() {
        return Clock.systemUTC();
    }

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
    VectorStore plantVectorStore(QdrantClient client, EmbeddingModel embeddingModel, RagProperties properties,
                                 @Qualifier("embeddingBatchingStrategy") BatchingStrategy batchingStrategy) {
        return vectorStore(client, embeddingModel, properties.getQdrant().getPlantCollection(), properties, batchingStrategy);
    }

    @Bean("plantEntityVectorStore")
    VectorStore plantEntityVectorStore(QdrantClient client, EmbeddingModel embeddingModel, RagProperties properties,
                                       @Qualifier("embeddingBatchingStrategy") BatchingStrategy batchingStrategy) {
        return vectorStore(client, embeddingModel, properties.getQdrant().getPlantEntityCollection(), properties,
                batchingStrategy);
    }

    @Bean("communityVectorStore")
    VectorStore communityVectorStore(QdrantClient client, EmbeddingModel embeddingModel, RagProperties properties,
                                     @Qualifier("embeddingBatchingStrategy") BatchingStrategy batchingStrategy) {
        return vectorStore(client, embeddingModel, properties.getQdrant().getCommunityCollection(), properties,
                batchingStrategy);
    }

    @Bean("diseaseVectorStore")
    VectorStore diseaseVectorStore(QdrantClient client, EmbeddingModel embeddingModel, RagProperties properties,
                                   @Qualifier("embeddingBatchingStrategy") BatchingStrategy batchingStrategy) {
        return vectorStore(client, embeddingModel, properties.getQdrant().getDiseaseCollection(), properties,
                batchingStrategy);
    }

    private VectorStore vectorStore(QdrantClient client, EmbeddingModel embeddingModel,
                                    String collection, RagProperties properties, BatchingStrategy batchingStrategy) {
        return QdrantVectorStore.builder(client, embeddingModel)
                .collectionName(collection)
                .initializeSchema(properties.getQdrant().isInitializeSchema())
                .batchingStrategy(batchingStrategy)
                .build();
    }

    @Bean("embeddingBatchingStrategy")
    BatchingStrategy embeddingBatchingStrategy(RagProperties properties) {
        var ingestion = properties.getIngestion();
        return new TokenCountBatchingStrategy(EncodingType.CL100K_BASE, ingestion.getEmbeddingBatchMaxTokens(),
                ingestion.getEmbeddingBatchReservePercentage());
    }

    @Bean
    ChatClient chatClient(ChatModel chatModel) {
        return ChatClient.create(chatModel);
    }

    @Bean
    PlantEntityDisambiguator.StructuredCaller entityResolutionStructuredCaller(
            OpenAiConnectionProperties commonProperties, OpenAiChatProperties chatProperties,
            RagProperties ragProperties, ObservationRegistry observationRegistry) {
        var connection = OpenAIAutoConfigurationUtil.resolveConnectionProperties(
                commonProperties, chatProperties, "chat");
        var entity = ragProperties.getEntityResolution();
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(entity.getLlmConnectTimeoutMillis())).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofMillis(entity.getLlmReadTimeoutMillis()));
        OpenAiApi api = OpenAiApi.builder()
                .baseUrl(connection.baseUrl())
                .apiKey(connection.apiKey())
                .headers(connection.headers())
                .completionsPath(chatProperties.getCompletionsPath())
                .restClientBuilder(RestClient.builder().requestFactory(requestFactory))
                .build();
        String entityModel = entity.getLlmModel();
        if (entityModel == null || entityModel.isBlank()) {
            entityModel = chatProperties.getOptions().getModel();
        }
        OpenAiChatOptions entityOptions = OpenAiChatOptions.builder()
                .model(entityModel)
                .temperature(entity.getLlmTemperature())
                .maxTokens(entity.getLlmMaxTokens())
                .extraBody(Map.of("enable_thinking", entity.isLlmEnableThinking()))
                .build();
        OpenAiChatModel model = OpenAiChatModel.builder()
                .openAiApi(api)
                .defaultOptions(entityOptions)
                .retryTemplate(RetryTemplate.builder().maxAttempts(1).noBackoff().build())
                .observationRegistry(observationRegistry)
                .build();
        ChatClient client = ChatClient.create(model);
        return (systemPrompt, userPrompt) -> {
            BeanOutputConverter<PlantEntityDisambiguator.LlmDecision> converter =
                    new BeanOutputConverter<>(PlantEntityDisambiguator.LlmDecision.class);
            return client.prompt()
                    .system(systemPrompt + "\n" + converter.getFormat())
                    .user(userPrompt)
                    .call()
                    .entity(converter);
        };
    }

    @Bean("rerankerRestClient")
    RestClient rerankerRestClient(RagProperties properties) {
        RestClient.Builder builder = RestClient.builder().baseUrl(properties.getReranker().getBaseUrl());
        if (properties.getReranker().getApiKey() != null && !properties.getReranker().getApiKey().isBlank()) {
            builder.defaultHeader("Authorization", "Bearer " + properties.getReranker().getApiKey());
        }
        return builder.build();
    }

    @Bean("plantStateRestClient")
    RestClient plantStateRestClient(RagProperties properties) {
        var state = properties.getPlantState();
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(state.getConnectTimeoutMillis())).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofMillis(state.getReadTimeoutMillis()));
        RestClient.Builder builder = RestClient.builder().baseUrl(state.getBaseUrl()).requestFactory(requestFactory);
        if (state.getApiKey() != null && !state.getApiKey().isBlank()) {
            builder.defaultHeader("X-Internal-Api-Key", state.getApiKey());
        }
        return builder.build();
    }

    @Bean("diseaseDetectorRestClient")
    RestClient diseaseDetectorRestClient(RagProperties properties) {
        var detector = properties.getDiseaseDetector();
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(detector.getConnectTimeoutMillis())).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofMillis(detector.getReadTimeoutMillis()));
        return RestClient.builder().baseUrl(detector.getBaseUrl()).requestFactory(requestFactory).build();
    }
}
