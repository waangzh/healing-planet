package com.healingplanet.ai.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
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
    private final RagExternalClientManager externalClientManager;

    public RagConfigService(RagConfigRepository repository, RagRuntimeConfigProvider runtimeConfigProvider,
                            RagRuntimeConfigValidator validator, RagProperties defaults, ObjectMapper objectMapper) {
        this(repository, runtimeConfigProvider, validator, defaults, objectMapper, null);
    }

    @Autowired
    public RagConfigService(RagConfigRepository repository, RagRuntimeConfigProvider runtimeConfigProvider,
                            RagRuntimeConfigValidator validator, RagProperties defaults, ObjectMapper objectMapper,
                            RagExternalClientManager externalClientManager) {
        this.repository = repository;
        this.runtimeConfigProvider = runtimeConfigProvider;
        this.validator = validator;
        this.defaults = defaults;
        this.objectMapper = objectMapper;
        this.externalClientManager = externalClientManager;
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
        activateOnStartup(normalize(active.config()));
    }

    public RagConfigRevisionView current() {
        return view(repository.findActive().orElseThrow(() -> new IllegalStateException("未找到已发布的 RAG 配置")));
    }

    public List<RagConfigRevisionView> revisions() {
        return repository.findAll().stream().map(this::view).toList();
    }

    public RagConfigRevisionView revision(long revision) {
        return view(find(revision));
    }

    @Transactional
    public RagConfigRevisionView saveDraft(RagConfigDraftRequest request) {
        RagRuntimeConfig config = requireValid(request == null ? null : request.config());
        String operator = operator(request.operator());
        RagConfigRevision created = repository.insert(RagConfigStatus.DRAFT, config, cleanDescription(request.description()),
                operator, null);
        repository.audit(created.revision(), "DRAFT_SAVED", operator, null, configJson(config));
        return view(created);
    }

    @Transactional
    public RagConfigValidationResult validate(long revision, String operator) {
        RagConfigRevision target = findForUpdate(revision);
        if (target.status() != RagConfigStatus.DRAFT && target.status() != RagConfigStatus.VALIDATED) {
            throw new IllegalStateException("只有草稿或已校验版本可以校验");
        }
        List<String> errors = validator.validate(normalize(target.config()));
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
        RagRuntimeConfig targetConfig = normalize(target.config());
        List<String> errors = validator.validate(targetConfig);
        if (!errors.isEmpty()) throw new IllegalStateException("配置校验未通过：" + String.join("；", errors));
        RagRuntimeSnapshot runtimeSnapshot = prepareRuntime(targetConfig);
        RagConfigRevision current = repository.findActiveForUpdate().orElse(null);
        String safeOperator = operator(operator);
        if (current != null) repository.markSuperseded(current.revision());
        repository.markActive(revision, safeOperator, null);
        RagConfigRevision active = find(revision);
        repository.audit(revision, "PUBLISHED", safeOperator, current == null ? null : current.revision(),
                diff(current == null ? null : normalize(current.config()), normalize(active.config())));
        activateAfterCommit(runtimeSnapshot);
        return view(active);
    }

    @Transactional
    public RagConfigRevisionView rollback(long revision, String operator) {
        RagConfigRevision target = findForUpdate(revision);
        if (target.status() == RagConfigStatus.DRAFT || target.status() == RagConfigStatus.FAILED) {
            throw new IllegalStateException("只能回滚到曾校验或发布过的版本");
        }
        RagRuntimeConfig targetConfig = normalize(target.config());
        List<String> errors = validator.validate(targetConfig);
        if (!errors.isEmpty()) throw new IllegalStateException("目标版本已不符合当前校验规则：" + String.join("；", errors));
        RagConfigRevision current = repository.findActiveForUpdate()
                .orElseThrow(() -> new IllegalStateException("未找到当前已发布版本"));
        if (current.revision() == revision) return view(current);
        RagRuntimeSnapshot runtimeSnapshot = prepareRuntime(targetConfig);
        String safeOperator = operator(operator);
        repository.markSuperseded(current.revision());
        repository.markActive(revision, safeOperator, current.revision());
        RagConfigRevision active = find(revision);
        repository.audit(revision, "ROLLED_BACK", safeOperator, current.revision(),
                diff(normalize(current.config()), normalize(active.config())));
        activateAfterCommit(runtimeSnapshot);
        return view(active);
    }

    @Transactional
    public RagConnectionTestResult testConnections(long revision, String operator) {
        RagConfigRevision target = findForUpdate(revision);
        if (target.status() == RagConfigStatus.FAILED) throw new IllegalStateException("失败版本不能用于连接测试");
        RagConnectionTestResult result = externalClientManager == null
                ? new RagConnectionTestResult(revision, true, List.of(), java.time.Instant.now())
                : externalClientManager.test(normalize(target.config()), revision);
        repository.audit(revision, result.successful() ? "CONNECTION_TESTED" : "CONNECTION_TEST_FAILED", operator(operator),
                null, json(Map.of("successful", result.successful(), "checks", result.checks())));
        return result;
    }

    public List<RagConnectionProfileView> connectionProfiles() {
        return externalClientManager == null ? List.of(new RagConnectionProfileView("default", "默认重排连接"))
                : externalClientManager.profiles();
    }

    /** 多实例轮询入口：仅在数据库 ACTIVE 版本领先于本机时重新探测并原子切换。 */
    public boolean refreshActiveRuntime() {
        RagConfigRevision active = repository.findActive()
                .orElseThrow(() -> new IllegalStateException("未找到已发布的 RAG 配置"));
        RagRuntimeConfig config = normalize(active.config());
        if (runtimeConfigProvider.snapshot().revision() == config.revision()) return false;
        RagRuntimeSnapshot snapshot = prepareRuntime(config);
        runtimeConfigProvider.activate(snapshot);
        return true;
    }

    private RagRuntimeConfig requireValid(RagRuntimeConfig config) {
        RagRuntimeConfig completed = normalize(config);
        List<String> errors = validator.validate(completed);
        if (!errors.isEmpty()) throw new IllegalArgumentException("配置校验未通过：" + String.join("；", errors));
        return completed.withRevision(0);
    }

    private RagConfigRevision find(long revision) {
        return repository.findByRevision(revision).orElseThrow(() -> new IllegalArgumentException("配置版本不存在：" + revision));
    }

    private RagConfigRevision findForUpdate(long revision) {
        return repository.findByRevisionForUpdate(revision)
                .orElseThrow(() -> new IllegalArgumentException("配置版本不存在：" + revision));
    }

    private void activateAfterCommit(RagRuntimeSnapshot snapshot) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            runtimeConfigProvider.activate(snapshot);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                runtimeConfigProvider.activate(snapshot);
            }
        });
    }

    private void activateOnStartup(RagRuntimeConfig config) {
        if (externalClientManager == null) {
            runtimeConfigProvider.activate(config);
            return;
        }
        runtimeConfigProvider.activate(externalClientManager.buildWithoutProbe(config));
    }

    private RagRuntimeSnapshot prepareRuntime(RagRuntimeConfig config) {
        return externalClientManager == null ? new RagRuntimeSnapshot(config, null) : externalClientManager.prepare(config);
    }

    private RagRuntimeConfig normalize(RagRuntimeConfig config) {
        if (config == null) return null;
        return config.completeWith(RagRuntimeConfig.from(defaults)).withRevision(config.revision());
    }

    private RagConfigRevisionView view(RagConfigRevision revision) {
        return new RagConfigRevisionView(revision.revision(), revision.status(), normalize(revision.config()),
                revision.description(), revision.createdBy(), revision.createdAt(), revision.validatedBy(),
                revision.validatedAt(), revision.publishedBy(), revision.publishedAt(), revision.rollbackFromRevision(),
                revision.failureReason());
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
