package com.healingplanet.ai.config;

import org.springframework.ai.model.openai.autoconfigure.OpenAiConnectionProperties;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 候选配置先在这里构建和探测。任何探测失败都不会替换当前运行时快照。
 * 密钥只读取部署侧配置，绝不写入 rag_config_revision 或返回给管理端。
 */
@Component
public class RagExternalClientManager {
    private final RagProperties properties;
    private final OpenAiConnectionProperties openAiProperties;

    public RagExternalClientManager(RagProperties properties, OpenAiConnectionProperties openAiProperties) {
        this.properties = properties;
        this.openAiProperties = openAiProperties;
    }

    public RagRuntimeSnapshot prepare(RagRuntimeConfig config) {
        RagConnectionTestResult result = test(config, config.revision());
        if (!result.successful()) {
            String failures = result.checks().stream().filter(check -> !check.successful())
                    .map(check -> check.name() + "：" + check.message()).reduce((left, right) -> left + "；" + right)
                    .orElse("外部连接测试未通过");
            throw new IllegalStateException("外部连接测试未通过：" + failures);
        }
        return buildWithoutProbe(config);
    }

    public RagRuntimeSnapshot buildWithoutProbe(RagRuntimeConfig config) {
        RagRuntimeSnapshot.RerankerRuntimeClient reranker = config.rerankerEnabled()
                ? buildReranker(config.rerankerClient()) : null;
        return new RagRuntimeSnapshot(config, reranker);
    }

    public RagConnectionTestResult test(RagRuntimeConfig config, long revision) {
        List<RagConnectionTestResult.Check> checks = new ArrayList<>();
        checks.add(probe("聊天模型网关", () -> chatProbeClient().get().uri(normalizedPath(
                properties.getGeneration().getHealthPath())).retrieve().toBodilessEntity()));
        if (config.rerankerEnabled()) {
            RagProperties.RerankerConnection connection = resolve(config.rerankerClient().connectionId());
            checks.add(probe("重排服务（" + config.rerankerClient().connectionId() + "）", () ->
                    restClient(connection).get().uri(normalizedPath(connection.getHealthPath())).retrieve().toBodilessEntity()));
        }
        return new RagConnectionTestResult(revision, checks.stream().allMatch(RagConnectionTestResult.Check::successful),
                List.copyOf(checks), Instant.now());
    }

    public List<RagConnectionProfileView> profiles() {
        List<RagConnectionProfileView> result = new ArrayList<>();
        result.add(new RagConnectionProfileView("default", "默认重排连接"));
        for (Map.Entry<String, RagProperties.RerankerConnection> entry : properties.getRerankerConnections().entrySet()) {
            if (!"default".equals(entry.getKey())) {
                String label = entry.getValue().getLabel();
                result.add(new RagConnectionProfileView(entry.getKey(), label == null || label.isBlank()
                        ? entry.getKey() : label));
            }
        }
        return List.copyOf(result);
    }

    private RagRuntimeSnapshot.RerankerRuntimeClient buildReranker(RagRuntimeConfig.RerankerClient clientConfig) {
        RagProperties.RerankerConnection connection = resolve(clientConfig.connectionId());
        return new RagRuntimeSnapshot.RerankerRuntimeClient(restClient(connection), clientConfig.path(),
                clientConfig.model(), clientConfig.candidateTopK(), clientConfig.maxFragmentsPerLogicalEvidence(),
                clientConfig.maxFragmentsTotal(), clientConfig.connectionId());
    }

    private RagProperties.RerankerConnection resolve(String connectionId) {
        if ("default".equals(connectionId)) {
            RagProperties.Reranker legacy = properties.getReranker();
            RagProperties.RerankerConnection fallback = new RagProperties.RerankerConnection();
            fallback.setLabel("默认重排连接");
            fallback.setBaseUrl(legacy.getBaseUrl());
            fallback.setApiKey(legacy.getApiKey());
            return fallback;
        }
        RagProperties.RerankerConnection connection = properties.getRerankerConnections().get(connectionId);
        if (connection == null || connection.getBaseUrl() == null || connection.getBaseUrl().isBlank()) {
            throw new IllegalArgumentException("未配置重排连接 profile：" + connectionId);
        }
        return connection;
    }

    private RestClient chatProbeClient() {
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(5));
        RestClient.Builder builder = RestClient.builder().baseUrl(openAiProperties.getBaseUrl()).requestFactory(requestFactory);
        String apiKey = openAiProperties.getApiKey();
        if (apiKey != null && !apiKey.isBlank()) builder.defaultHeader("Authorization", "Bearer " + apiKey);
        return builder.build();
    }

    private RestClient restClient(RagProperties.RerankerConnection connection) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(connection.getConnectTimeoutMillis())).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofMillis(connection.getReadTimeoutMillis()));
        RestClient.Builder builder = RestClient.builder().baseUrl(connection.getBaseUrl()).requestFactory(requestFactory);
        if (connection.getApiKey() != null && !connection.getApiKey().isBlank()) {
            builder.defaultHeader("Authorization", "Bearer " + connection.getApiKey());
        }
        return builder.build();
    }

    private RagConnectionTestResult.Check probe(String name, Probe action) {
        try {
            action.run();
            return new RagConnectionTestResult.Check(name, true, "连接正常");
        } catch (RestClientResponseException exception) {
            return new RagConnectionTestResult.Check(name, false, "服务返回 HTTP " + exception.getStatusCode().value());
        } catch (RestClientException exception) {
            return new RagConnectionTestResult.Check(name, false, "无法建立连接或请求超时");
        } catch (RuntimeException exception) {
            return new RagConnectionTestResult.Check(name, false, "连接配置不可用");
        }
    }

    private String normalizedPath(String path) {
        return path == null || path.isBlank() ? "/" : path.startsWith("/") ? path : "/" + path;
    }

    @FunctionalInterface
    private interface Probe {
        void run();
    }
}
