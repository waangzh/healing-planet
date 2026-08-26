package com.green.service;

import com.green.config.RagAdminProperties;
import com.green.outbox.PostIndexEventMessage;
import com.green.outbox.PostIndexEventType;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

/** 仅供 RabbitMQ 消费者调用的 AI 内部索引客户端。 */
@Service
public class RagPostIndexClient {
    private final RestTemplate restTemplate;
    private final RagAdminProperties properties;

    public RagPostIndexClient(@Qualifier("ragAdminRestTemplate") RestTemplate restTemplate,
                              RagAdminProperties properties) {
        this.restTemplate = restTemplate;
        this.properties = properties;
    }

    public void apply(PostIndexEventMessage event) {
        if (!StringUtils.hasText(properties.getInternalApiKey())) {
            throw new IllegalStateException("RAG_INTERNAL_API_KEY 未配置，不能消费帖子索引事件");
        }
        PostIndexEventType type;
        try {
            type = PostIndexEventType.valueOf(event.getType());
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("不支持的帖子索引事件类型: " + event.getType(), exception);
        }

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Internal-Api-Key", properties.getInternalApiKey());
        HttpMethod method = type == PostIndexEventType.POST_DELETE ? HttpMethod.DELETE : HttpMethod.POST;
        ResponseEntity<Void> response = restTemplate.exchange(postUrl(event.getPostId()), method,
                new HttpEntity<Void>(headers), Void.class);
        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new IllegalStateException("RAG 索引接口返回非成功状态: " + response.getStatusCodeValue());
        }
    }

    private String postUrl(String postId) {
        String baseUrl = properties.getAiBaseUrl();
        if (!StringUtils.hasText(baseUrl)) {
            throw new IllegalStateException("RAG 服务地址未配置");
        }
        return UriComponentsBuilder.fromHttpUrl(baseUrl.endsWith("/")
                        ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl)
                .pathSegment("internal", "index", "post", postId)
                .toUriString();
    }
}
