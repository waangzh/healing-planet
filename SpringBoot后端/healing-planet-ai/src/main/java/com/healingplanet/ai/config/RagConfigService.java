package com.healingplanet.ai.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class RagConfigService {
    private final RagConfigRepository repository;
    private final RagRuntimeConfigProvider runtimeConfigProvider;
    private final RagRuntimeConfigValidator validator;
    private final RagProperties defaults;
    private final ObjectMapper objectMapper;

    public RagConfigService(RagConfigRepository repository, RagRuntimeConfigProvider runtimeConfigProvider,
                            RagRuntimeConfigValidator validator, RagProperties defaults, ObjectMapper objectMapper) {
        this.repository = repository;
        this.runtimeConfigProvider = runtimeConfigProvider;
        this.validator = validator;
        this.defaults = defaults;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    @Transactional
    public void initialize() {
        repository.ensureSchema();
        RagConfigRevision active = repository.findActive().orElseGet(() -> {
            RagConfigRevision created = repository.insert(RagConfigStatus.ACTIVE, RagRuntimeConfig.from(defaults),
                    "由 application.yml 初始化", "system", null);
            repository.audit(created.revision(), "BOOTSTRAPPED", "system", null, "{}");
            return created;
        });
        runtimeConfigProvider.activate(active.config());
    }

    public RagConfigRevisionView current() {
        return RagConfigRevisionView.from(repository.findActive()
                .orElseThrow(() -> new IllegalStateException("未找到已发布的 RAG 配置")));
    }

    public List<RagConfigRevisionView> revisions() {
        return repository.findAll().stream().map(RagConfigRevisionView::from).toList();
    }

    public RagConfigRevisionView revision(long revision) {
        return RagConfigRevisionView.from(find(revision));
    }

    @Transactional
    public RagConfigRevisionView saveDraft(RagConfigDraftRequest request) {
        RagRuntimeConfig config = requireValid(request == null ? null : request.config());
        String operator = operator(request.operator());
        RagConfigRevision created = repository.insert(RagConfigStatus.DRAFT, config, cleanDescription(request.description()),
                operator, null);
        repository.audit(created.revision(), "DRAFT_SAVED", operator, null, configJson(config));
        return RagConfigRevisionView.from(created);
    }

    @Transactional
    public RagConfigValidationResult validate(long revision, String operator) {
        RagConfigRevision target = findForUpdate(revision);
        if (target.status() != RagConfigStatus.DRAFT && target.status() != RagConfigStatus.VALIDATED) {
            throw new IllegalStateException("只有草稿或已校验版本可以校验");
        }
        List<String> errors = validator.validate(target.config());
        String safeOperator = operator(operator);
        if (!errors.isEmpty()) {
            repository.audit(revision, "VALIDATION_FAILED", safeOperator, null, json(Map.of("errors", errors)));
            return new RagConfigValidationResult(revision, false, errors);
        }
        repository.markValidated(revision, safeOperator);
        repository.audit(revision, "VALIDATED", safeOperator, null, "{}");
        return new RagConfigValidationResult(revision, true, List.of());
    }

    @Transactional
    public RagConfigRevisionView publish(long revision, String operator) {
        RagConfigRevision target = findForUpdate(revision);
        if (target.status() != RagConfigStatus.VALIDATED) {
            throw new IllegalStateException("请先校验草稿后再发布");
        }
        List<String> errors = validator.validate(target.config());
        if (!errors.isEmpty()) throw new IllegalStateException("配置校验未通过：" + String.join("；", errors));
        RagConfigRevision current = repository.findActiveForUpdate().orElse(null);
        String safeOperator = operator(operator);
        if (current != null) repository.markSuperseded(current.revision());
        repository.markActive(revision, safeOperator, null);
        RagConfigRevision active = find(revision);
        repository.audit(revision, "PUBLISHED", safeOperator, current == null ? null : current.revision(),
                diff(current == null ? null : current.config(), active.config()));
        activateAfterCommit(active.config());
        return RagConfigRevisionView.from(active);
    }

    @Transactional
    public RagConfigRevisionView rollback(long revision, String operator) {
        RagConfigRevision target = findForUpdate(revision);
        if (target.status() == RagConfigStatus.DRAFT || target.status() == RagConfigStatus.FAILED) {
            throw new IllegalStateException("只能回滚到曾校验或发布过的版本");
        }
        List<String> errors = validator.validate(target.config());
        if (!errors.isEmpty()) throw new IllegalStateException("目标版本已不符合当前校验规则：" + String.join("；", errors));
        RagConfigRevision current = repository.findActiveForUpdate()
                .orElseThrow(() -> new IllegalStateException("未找到当前已发布版本"));
        if (current.revision() == revision) return RagConfigRevisionView.from(current);
        String safeOperator = operator(operator);
        repository.markSuperseded(current.revision());
        repository.markActive(revision, safeOperator, current.revision());
        RagConfigRevision active = find(revision);
        repository.audit(revision, "ROLLED_BACK", safeOperator, current.revision(), diff(current.config(), active.config()));
        activateAfterCommit(active.config());
        return RagConfigRevisionView.from(active);
    }

    private RagRuntimeConfig requireValid(RagRuntimeConfig config) {
        List<String> errors = validator.validate(config);
        if (!errors.isEmpty()) throw new IllegalArgumentException("配置校验未通过：" + String.join("；", errors));
        return config.withRevision(0);
    }

    private RagConfigRevision find(long revision) {
        return repository.findByRevision(revision).orElseThrow(() -> new IllegalArgumentException("配置版本不存在：" + revision));
    }

    private RagConfigRevision findForUpdate(long revision) {
        return repository.findByRevisionForUpdate(revision)
                .orElseThrow(() -> new IllegalArgumentException("配置版本不存在：" + revision));
    }

    private void activateAfterCommit(RagRuntimeConfig config) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            runtimeConfigProvider.activate(config);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                runtimeConfigProvider.activate(config);
            }
        });
    }

    private String diff(RagRuntimeConfig before, RagRuntimeConfig after) {
        if (before == null) return configJson(after);
        Map<String, Object> beforeMap = objectMapper.convertValue(before, Map.class);
        Map<String, Object> afterMap = objectMapper.convertValue(after, Map.class);
        Map<String, Object> changes = new LinkedHashMap<>();
        afterMap.forEach((key, value) -> {
            if (!Objects.deepEquals(beforeMap.get(key), value)) {
                changes.put(key, Map.of("before", beforeMap.get(key), "after", value));
            }
        });
        return json(Map.of("changes", changes));
    }

    private String configJson(RagRuntimeConfig config) {
        return json(Map.of("config", config));
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("审计数据无法序列化", exception);
        }
    }

    private String operator(String value) {
        return value == null || value.isBlank() ? "system" : value.trim();
    }

    private String cleanDescription(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim().substring(0, Math.min(value.trim().length(), 500));
    }
}
