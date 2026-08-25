package com.green.service;

import com.green.config.RagAdminProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.util.Collections;
import java.util.Map;

/** 管理端 BFF 到 AI 服务的唯一入口，浏览器不会持有内部服务密钥。 */
@Service
public class RagAdminClient {
    private final RestTemplate restTemplate;
    private final RagAdminProperties properties;

    public RagAdminClient(@Qualifier("ragAdminRestTemplate") RestTemplate restTemplate,
                          RagAdminProperties properties) {
        this.restTemplate = restTemplate;
        this.properties = properties;
    }

    public Object current() { return exchange(HttpMethod.GET, "/current", null); }
    public Object revisions() { return exchange(HttpMethod.GET, "/revisions", null); }
    public Object revision(long revision) { return exchange(HttpMethod.GET, "/revisions/" + revision, null); }
    public Object saveDraft(Map<String, Object> body) { return exchange(HttpMethod.POST, "/drafts", body); }
    public Object validate(long revision, String operator) {
        return exchange(HttpMethod.POST, "/drafts/" + revision + "/validate", Collections.singletonMap("operator", operator));
    }
    public Object publish(long revision, String operator) {
        return exchange(HttpMethod.POST, "/drafts/" + revision + "/publish", Collections.singletonMap("operator", operator));
    }
    public Object rollback(long revision, String operator) {
        return exchange(HttpMethod.POST, "/revisions/" + revision + "/rollback", Collections.singletonMap("operator", operator));
    }

    private Object exchange(HttpMethod method, String path, Object body) {
        String key = properties.getInternalApiKey();
        if (key == null || key.trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "RAG 管理服务未配置内部访问密钥");
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Internal-Api-Key", key);
        try {
            ResponseEntity<Object> response = restTemplate.exchange(baseUrl() + "/internal/rag-config" + path,
                    method, new HttpEntity<Object>(body, headers), Object.class);
            return response.getBody();
        } catch (HttpStatusCodeException exception) {
            throw new ResponseStatusException(exception.getStatusCode(), "RAG 配置服务拒绝请求");
        } catch (RestClientException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "无法连接 RAG 配置服务");
        }
    }

    private String baseUrl() {
        String value = properties.getAiBaseUrl();
        return value != null && value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
