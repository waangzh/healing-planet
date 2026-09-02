package com.healingplanet.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;

@ConfigurationProperties(prefix = "app.rag")
public class RagProperties {

    private int denseTopK = 30;
    private int sparseTopK = 30;
    private int finalTopK = 6;
    private double similarityThreshold = 0.25;
    private RetrievalMode retrievalMode = RetrievalMode.HYBRID_RRF;
    private int rrfK = 60;
    private final AdaptiveRecall adaptiveRecall = new AdaptiveRecall();
    private final RecallQualification recallQualification = new RecallQualification();
    private String internalApiKey = "";
    private Path dataDirectory = Path.of("data", "rag");
    private final Ingestion ingestion = new Ingestion();
    private final IndexObservability indexObservability = new IndexObservability();
    private final Qdrant qdrant = new Qdrant();
    private final EntityResolution entityResolution = new EntityResolution();
    private final Reranker reranker = new Reranker();
    private final Bm25 bm25 = new Bm25();
    private final Generation generation = new Generation();
    private final SourceAwareRanking sourceAwareRanking = new SourceAwareRanking();
    private final EvidenceSelector evidenceSelector = new EvidenceSelector();
    private final ContextAssembly contextAssembly = new ContextAssembly();
    private final Answerability answerability = new Answerability();
    private final PlantState plantState = new PlantState();
    private final DiseaseDetector diseaseDetector = new DiseaseDetector();
    private final Attachments attachments = new Attachments();
    private final Eval eval = new Eval();
    /** 连接配置仅由部署侧维护；运行时版本只引用 profile id，避免密钥进入数据库和管理端。 */
    private final Map<String, RerankerConnection> rerankerConnections = new LinkedHashMap<>();

    public int getDenseTopK() { return denseTopK; }
    public void setDenseTopK(int denseTopK) { this.denseTopK = denseTopK; }
    public int getSparseTopK() { return sparseTopK; }
    public void setSparseTopK(int sparseTopK) { this.sparseTopK = sparseTopK; }
    public int getFinalTopK() { return finalTopK; }
    public void setFinalTopK(int finalTopK) { this.finalTopK = finalTopK; }
    public double getSimilarityThreshold() { return similarityThreshold; }
    public void setSimilarityThreshold(double similarityThreshold) { this.similarityThreshold = similarityThreshold; }
    public RetrievalMode getRetrievalMode() { return retrievalMode; }
    public void setRetrievalMode(RetrievalMode retrievalMode) { this.retrievalMode = retrievalMode; }
    public int getRrfK() { return rrfK; }
    public void setRrfK(int rrfK) { this.rrfK = rrfK; }
    public AdaptiveRecall getAdaptiveRecall() { return adaptiveRecall; }
    public RecallQualification getRecallQualification() { return recallQualification; }
    public String getInternalApiKey() { return internalApiKey; }
    public void setInternalApiKey(String internalApiKey) { this.internalApiKey = internalApiKey; }
    public Path getDataDirectory() { return dataDirectory; }
    public void setDataDirectory(Path dataDirectory) { this.dataDirectory = dataDirectory; }
    public Ingestion getIngestion() { return ingestion; }
    public IndexObservability getIndexObservability() { return indexObservability; }
    public Qdrant getQdrant() { return qdrant; }
    public EntityResolution getEntityResolution() { return entityResolution; }
    public Reranker getReranker() { return reranker; }
    public Bm25 getBm25() { return bm25; }
    public Generation getGeneration() { return generation; }
    public SourceAwareRanking getSourceAwareRanking() { return sourceAwareRanking; }
    public EvidenceSelector getEvidenceSelector() { return evidenceSelector; }
    public ContextAssembly getContextAssembly() { return contextAssembly; }
    public Answerability getAnswerability() { return answerability; }
    public PlantState getPlantState() { return plantState; }
    public DiseaseDetector getDiseaseDetector() { return diseaseDetector; }
    public Attachments getAttachments() { return attachments; }
    public Eval getEval() { return eval; }
    public Map<String, RerankerConnection> getRerankerConnections() { return rerankerConnections; }

    public enum RetrievalMode {
        BM25_ONLY,
        DENSE_ONLY,
        HYBRID_RRF;

        public boolean usesDense() { return this != BM25_ONLY; }
        public boolean usesSparse() { return this != DENSE_ONLY; }
    }

    public static class AdaptiveRecall {
        private boolean enabled = true;
        private int maxDenseTopK = 120;
        private int maxSparseTopK = 120;
        private int minUniqueLogicalCandidates = 2;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public int getMaxDenseTopK() { return maxDenseTopK; }
        public void setMaxDenseTopK(int value) { this.maxDenseTopK = value; }
        public int getMaxSparseTopK() { return maxSparseTopK; }
        public void setMaxSparseTopK(int value) { this.maxSparseTopK = value; }
        public int getMinUniqueLogicalCandidates() { return minUniqueLogicalCandidates; }
        public void setMinUniqueLogicalCandidates(int value) { this.minUniqueLogicalCandidates = value; }
    }

    /** Conservative reranker floor used only to decide whether bounded recall should continue. */
    public static class RecallQualification {
        private boolean enabled = true;
        private double minimumRerankScore = 0.20;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public double getMinimumRerankScore() { return minimumRerankScore; }
        public void setMinimumRerankScore(double value) { this.minimumRerankScore = value; }
    }

    public static class Ingestion {
        private int batchSize = 100;
        private String embeddingModelVersion = "BAAI/bge-m3";
        private String embeddingContentVersion = "embedding-content-v2";
        private String chunkSchemaVersion = "chunk-schema-v2";
        private int embeddingBatchMaxTokens = 8000;
        private double embeddingBatchReservePercentage = 0.1;
        private int plantGeneralCareMaxTokens = 800;
        private int communityMaxTokens = 800;
        private int diseaseMaxTokens = 800;
        /** A source-level lease prevents a full scan and an outbox-driven incremental run from interleaving. */
        private Duration sourceLockLeaseDuration = Duration.ofMinutes(5);
        private Duration sourceLockAcquireTimeout = Duration.ofMinutes(10);
        private Duration sourceLockRetryDelay = Duration.ofMillis(200);

        public int getBatchSize() { return batchSize; }
        public void setBatchSize(int batchSize) { this.batchSize = batchSize; }
        public String getEmbeddingModelVersion() { return embeddingModelVersion; }
        public void setEmbeddingModelVersion(String embeddingModelVersion) {
            this.embeddingModelVersion = embeddingModelVersion;
        }
        public String getEmbeddingContentVersion() { return embeddingContentVersion; }
        public void setEmbeddingContentVersion(String value) { this.embeddingContentVersion = value; }
        public String getChunkSchemaVersion() { return chunkSchemaVersion; }
        public void setChunkSchemaVersion(String value) { this.chunkSchemaVersion = value; }
        public int getEmbeddingBatchMaxTokens() { return embeddingBatchMaxTokens; }
        public void setEmbeddingBatchMaxTokens(int embeddingBatchMaxTokens) {
            this.embeddingBatchMaxTokens = embeddingBatchMaxTokens;
        }
        public double getEmbeddingBatchReservePercentage() { return embeddingBatchReservePercentage; }
        public void setEmbeddingBatchReservePercentage(double embeddingBatchReservePercentage) {
            this.embeddingBatchReservePercentage = embeddingBatchReservePercentage;
        }
        public int getPlantGeneralCareMaxTokens() { return plantGeneralCareMaxTokens; }
        public void setPlantGeneralCareMaxTokens(int value) { this.plantGeneralCareMaxTokens = value; }
        public int getCommunityMaxTokens() { return communityMaxTokens; }
        public void setCommunityMaxTokens(int value) { this.communityMaxTokens = value; }
        public int getDiseaseMaxTokens() { return diseaseMaxTokens; }
        public void setDiseaseMaxTokens(int value) { this.diseaseMaxTokens = value; }
        public Duration getSourceLockLeaseDuration() { return sourceLockLeaseDuration; }
        public void setSourceLockLeaseDuration(Duration value) { this.sourceLockLeaseDuration = value; }
        public Duration getSourceLockAcquireTimeout() { return sourceLockAcquireTimeout; }
        public void setSourceLockAcquireTimeout(Duration value) { this.sourceLockAcquireTimeout = value; }
        public Duration getSourceLockRetryDelay() { return sourceLockRetryDelay; }
        public void setSourceLockRetryDelay(Duration value) { this.sourceLockRetryDelay = value; }
    }

    public static class IndexObservability {
        /** The stale-age threshold controls the alert and alerting gauge. */
        private Duration sourceLagAlertThreshold = Duration.ofMinutes(15);
        /** Period for the automatic read-only freshness probe; it never starts ingestion. */
        private Duration freshnessRefreshInterval = Duration.ofMinutes(1);
        private Duration freshnessRefreshInitialDelay = Duration.ofSeconds(15);

        public Duration getSourceLagAlertThreshold() { return sourceLagAlertThreshold; }
        public void setSourceLagAlertThreshold(Duration value) { this.sourceLagAlertThreshold = value; }
        public Duration getFreshnessRefreshInterval() { return freshnessRefreshInterval; }
        public void setFreshnessRefreshInterval(Duration value) { this.freshnessRefreshInterval = value; }
        public Duration getFreshnessRefreshInitialDelay() { return freshnessRefreshInitialDelay; }
        public void setFreshnessRefreshInitialDelay(Duration value) { this.freshnessRefreshInitialDelay = value; }
    }

    public static class Generation {
        private String model = "Qwen/Qwen3.5-397B-A17B";
        private double temperature = 0.1;
        private int maxTokens = 1024;
        private String healthPath = "/v1/models";

        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }
        public double getTemperature() { return temperature; }
        public void setTemperature(double temperature) { this.temperature = temperature; }
        public int getMaxTokens() { return maxTokens; }
        public void setMaxTokens(int maxTokens) { this.maxTokens = maxTokens; }
        public String getHealthPath() { return healthPath; }
        public void setHealthPath(String healthPath) { this.healthPath = healthPath; }
    }

    public static class SourceAwareRanking {
        private boolean enabled = true;
        private double rrfNormalizationFactor = 31;
        private double denseWeight = 0.55;
        private double rrfWeight = 0.45;
        private double plantSemanticWeight = 0.70;
        private double plantTrustWeight = 0.20;
        private double plantMatchWeight = 0.10;
        private double communitySemanticWeight = 0.62;
        private double communityTrustWeight = 0.15;
        private double communityQualityWeight = 0.13;
        private double communityRecencyWeight = 0.05;
        private double communityPlantMatchWeight = 0.05;
        private double communityEssenceWeight = 0.20;
        private double communityEngagementWeight = 0.80;
        private double collectWeight = 2.0;
        private double commentWeight = 1.5;
        private double viewWeight = 0.05;
        private double engagementNormalization = 1000;
        private double recencyDecayDays = 365;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public double getRrfNormalizationFactor() { return rrfNormalizationFactor; }
        public void setRrfNormalizationFactor(double value) { this.rrfNormalizationFactor = value; }
        public double getDenseWeight() { return denseWeight; }
        public void setDenseWeight(double value) { this.denseWeight = value; }
        public double getRrfWeight() { return rrfWeight; }
        public void setRrfWeight(double value) { this.rrfWeight = value; }
        public double getPlantSemanticWeight() { return plantSemanticWeight; }
        public void setPlantSemanticWeight(double value) { this.plantSemanticWeight = value; }
        public double getPlantTrustWeight() { return plantTrustWeight; }
        public void setPlantTrustWeight(double value) { this.plantTrustWeight = value; }
        public double getPlantMatchWeight() { return plantMatchWeight; }
        public void setPlantMatchWeight(double value) { this.plantMatchWeight = value; }
        public double getCommunitySemanticWeight() { return communitySemanticWeight; }
        public void setCommunitySemanticWeight(double value) { this.communitySemanticWeight = value; }
        public double getCommunityTrustWeight() { return communityTrustWeight; }
        public void setCommunityTrustWeight(double value) { this.communityTrustWeight = value; }
        public double getCommunityQualityWeight() { return communityQualityWeight; }
        public void setCommunityQualityWeight(double value) { this.communityQualityWeight = value; }
        public double getCommunityRecencyWeight() { return communityRecencyWeight; }
        public void setCommunityRecencyWeight(double value) { this.communityRecencyWeight = value; }
        public double getCommunityPlantMatchWeight() { return communityPlantMatchWeight; }
        public void setCommunityPlantMatchWeight(double value) { this.communityPlantMatchWeight = value; }
        public double getCommunityEssenceWeight() { return communityEssenceWeight; }
        public void setCommunityEssenceWeight(double value) { this.communityEssenceWeight = value; }
        public double getCommunityEngagementWeight() { return communityEngagementWeight; }
        public void setCommunityEngagementWeight(double value) { this.communityEngagementWeight = value; }
        public double getCollectWeight() { return collectWeight; }
        public void setCollectWeight(double value) { this.collectWeight = value; }
        public double getCommentWeight() { return commentWeight; }
        public void setCommentWeight(double value) { this.commentWeight = value; }
        public double getViewWeight() { return viewWeight; }
        public void setViewWeight(double value) { this.viewWeight = value; }
        public double getEngagementNormalization() { return engagementNormalization; }
        public void setEngagementNormalization(double value) { this.engagementNormalization = value; }
        public double getRecencyDecayDays() { return recencyDecayDays; }
        public void setRecencyDecayDays(double value) { this.recencyDecayDays = value; }
    }

    public static class EvidenceSelector {
        private boolean enabled = true;
        private int mixedSourceCommunityLimit = 2;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public int getMixedSourceCommunityLimit() { return mixedSourceCommunityLimit; }
        public void setMixedSourceCommunityLimit(int value) { this.mixedSourceCommunityLimit = value; }
    }

    public static class ContextAssembly {
        private int maxFragmentsPerLogicalEvidence = 2;

        public int getMaxFragmentsPerLogicalEvidence() { return maxFragmentsPerLogicalEvidence; }
        public void setMaxFragmentsPerLogicalEvidence(int value) { this.maxFragmentsPerLogicalEvidence = value; }
    }

    public static class Answerability {
        private double minRetrievalRelevance = 0.45;
        private double minRerankRelevance = 0.40;
        private double minAlignedSemanticRelevance = 0.30;
        private double minAlignedFinalRelevance = 0.60;
        private double strongRecoveryRelevance = 0.60;

        public double getMinRetrievalRelevance() { return minRetrievalRelevance; }
        public void setMinRetrievalRelevance(double value) { this.minRetrievalRelevance = value; }
        public double getMinRerankRelevance() { return minRerankRelevance; }
        public void setMinRerankRelevance(double value) { this.minRerankRelevance = value; }
        public double getMinAlignedSemanticRelevance() { return minAlignedSemanticRelevance; }
        public void setMinAlignedSemanticRelevance(double value) { this.minAlignedSemanticRelevance = value; }
        public double getMinAlignedFinalRelevance() { return minAlignedFinalRelevance; }
        public void setMinAlignedFinalRelevance(double value) { this.minAlignedFinalRelevance = value; }
        public double getStrongRecoveryRelevance() { return strongRecoveryRelevance; }
        public void setStrongRecoveryRelevance(double value) { this.strongRecoveryRelevance = value; }
    }

    public static class Bm25 {
        private float k1 = 1.2f;
        private float b = 0.75f;
        private int minNgram = 1;
        private int maxNgram = 3;

        public float getK1() { return k1; }
        public void setK1(float k1) { this.k1 = k1; }
        public float getB() { return b; }
        public void setB(float b) { this.b = b; }
        public int getMinNgram() { return minNgram; }
        public void setMinNgram(int minNgram) { this.minNgram = minNgram; }
        public int getMaxNgram() { return maxNgram; }
        public void setMaxNgram(int maxNgram) { this.maxNgram = maxNgram; }
    }

    public static class Eval {
        private Path fixtureDirectory = Path.of("..", "..", "rag-eval", "fixtures");
        private Instant clockInstant = Instant.parse("2026-08-17T02:00:00Z");
        private ZoneId clockZone = ZoneId.of("Asia/Shanghai");
        private boolean retrievalTraceEnabled;

        public Path getFixtureDirectory() { return fixtureDirectory; }
        public void setFixtureDirectory(Path fixtureDirectory) { this.fixtureDirectory = fixtureDirectory; }
        public Instant getClockInstant() { return clockInstant; }
        public void setClockInstant(Instant clockInstant) { this.clockInstant = clockInstant; }
        public ZoneId getClockZone() { return clockZone; }
        public void setClockZone(ZoneId clockZone) { this.clockZone = clockZone; }
        public boolean isRetrievalTraceEnabled() { return retrievalTraceEnabled; }
        public void setRetrievalTraceEnabled(boolean retrievalTraceEnabled) {
            this.retrievalTraceEnabled = retrievalTraceEnabled;
        }
    }

    public static class Attachments {
        private long ttlSeconds = 15 * 60;
        private int maxEntries = 32;

        public long getTtlSeconds() { return ttlSeconds; }
        public void setTtlSeconds(long ttlSeconds) { this.ttlSeconds = ttlSeconds; }
        public int getMaxEntries() { return maxEntries; }
        public void setMaxEntries(int maxEntries) { this.maxEntries = maxEntries; }
    }

    public static class DiseaseDetector {
        private String baseUrl = "http://localhost:5000";
        private String path = "/classify";
        private int connectTimeoutMillis = 2000;
        private int readTimeoutMillis = 15000;
        private long maxImageBytes = 10 * 1024 * 1024;

        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
        public String getPath() { return path; }
        public void setPath(String path) { this.path = path; }
        public int getConnectTimeoutMillis() { return connectTimeoutMillis; }
        public void setConnectTimeoutMillis(int connectTimeoutMillis) { this.connectTimeoutMillis = connectTimeoutMillis; }
        public int getReadTimeoutMillis() { return readTimeoutMillis; }
        public void setReadTimeoutMillis(int readTimeoutMillis) { this.readTimeoutMillis = readTimeoutMillis; }
        public long getMaxImageBytes() { return maxImageBytes; }
        public void setMaxImageBytes(long maxImageBytes) { this.maxImageBytes = maxImageBytes; }
    }

    public static class PlantState {
        private String baseUrl = "http://localhost:8070";
        private String apiKey = "";
        private int connectTimeoutMillis = 1000;
        private int readTimeoutMillis = 3000;

        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }
        public int getConnectTimeoutMillis() { return connectTimeoutMillis; }
        public void setConnectTimeoutMillis(int connectTimeoutMillis) { this.connectTimeoutMillis = connectTimeoutMillis; }
        public int getReadTimeoutMillis() { return readTimeoutMillis; }
        public void setReadTimeoutMillis(int readTimeoutMillis) { this.readTimeoutMillis = readTimeoutMillis; }
    }

    public static class Qdrant {
        private String host = "localhost";
        private int port = 6334;
        private boolean tls;
        private String apiKey = "";
        private String plantCollection = "plant_knowledge";
        private String plantEntityCollection = "plant_entities";
        private String communityCollection = "community_knowledge";
        private String diseaseCollection = "disease_knowledge";
        private boolean initializeSchema = true;

        public String getHost() { return host; }
        public void setHost(String host) { this.host = host; }
        public int getPort() { return port; }
        public void setPort(int port) { this.port = port; }
        public boolean isTls() { return tls; }
        public void setTls(boolean tls) { this.tls = tls; }
        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }
        public String getPlantCollection() { return plantCollection; }
        public void setPlantCollection(String plantCollection) { this.plantCollection = plantCollection; }
        public String getPlantEntityCollection() { return plantEntityCollection; }
        public void setPlantEntityCollection(String plantEntityCollection) { this.plantEntityCollection = plantEntityCollection; }
        public String getCommunityCollection() { return communityCollection; }
        public void setCommunityCollection(String communityCollection) { this.communityCollection = communityCollection; }
        public String getDiseaseCollection() { return diseaseCollection; }
        public void setDiseaseCollection(String diseaseCollection) { this.diseaseCollection = diseaseCollection; }
        public boolean isInitializeSchema() { return initializeSchema; }
        public void setInitializeSchema(boolean initializeSchema) { this.initializeSchema = initializeSchema; }
    }

    public static class EntityResolution {
        private int candidateTopK = 5;
        private double fuzzyHardThreshold = 0.90;
        private double fuzzySoftThreshold = 0.65;
        private double fuzzyHardMargin = 0.15;
        private double fuzzySoftMargin = 0.08;
        private boolean llmEnabled = true;
        private String llmModel = "";
        private double llmTemperature = 0;
        private int llmMaxTokens = 160;
        private boolean llmEnableThinking;
        private int llmMaxCandidates = 5;
        private double llmConfidenceThreshold = 0.90;
        private int llmCacheMaxEntries = 256;
        private int llmConnectTimeoutMillis = 1000;
        private int llmReadTimeoutMillis = 8000;
        private int circuitBreakerFailureThreshold = 3;
        private long circuitBreakerOpenMillis = 5000;

        public int getCandidateTopK() { return candidateTopK; }
        public void setCandidateTopK(int candidateTopK) { this.candidateTopK = candidateTopK; }
        public double getFuzzyHardThreshold() { return fuzzyHardThreshold; }
        public void setFuzzyHardThreshold(double value) { this.fuzzyHardThreshold = value; }
        public double getFuzzySoftThreshold() { return fuzzySoftThreshold; }
        public void setFuzzySoftThreshold(double value) { this.fuzzySoftThreshold = value; }
        public double getFuzzyHardMargin() { return fuzzyHardMargin; }
        public void setFuzzyHardMargin(double value) { this.fuzzyHardMargin = value; }
        public double getFuzzySoftMargin() { return fuzzySoftMargin; }
        public void setFuzzySoftMargin(double value) { this.fuzzySoftMargin = value; }
        public boolean isLlmEnabled() { return llmEnabled; }
        public void setLlmEnabled(boolean llmEnabled) { this.llmEnabled = llmEnabled; }
        public String getLlmModel() { return llmModel; }
        public void setLlmModel(String value) { this.llmModel = value; }
        public double getLlmTemperature() { return llmTemperature; }
        public void setLlmTemperature(double value) { this.llmTemperature = value; }
        public int getLlmMaxTokens() { return llmMaxTokens; }
        public void setLlmMaxTokens(int value) { this.llmMaxTokens = value; }
        public boolean isLlmEnableThinking() { return llmEnableThinking; }
        public void setLlmEnableThinking(boolean value) { this.llmEnableThinking = value; }
        public int getLlmMaxCandidates() { return llmMaxCandidates; }
        public void setLlmMaxCandidates(int llmMaxCandidates) { this.llmMaxCandidates = llmMaxCandidates; }
        public double getLlmConfidenceThreshold() { return llmConfidenceThreshold; }
        public void setLlmConfidenceThreshold(double value) { this.llmConfidenceThreshold = value; }
        public int getLlmCacheMaxEntries() { return llmCacheMaxEntries; }
        public void setLlmCacheMaxEntries(int value) { this.llmCacheMaxEntries = value; }
        public int getLlmConnectTimeoutMillis() { return llmConnectTimeoutMillis; }
        public void setLlmConnectTimeoutMillis(int value) { this.llmConnectTimeoutMillis = value; }
        public int getLlmReadTimeoutMillis() { return llmReadTimeoutMillis; }
        public void setLlmReadTimeoutMillis(int value) { this.llmReadTimeoutMillis = value; }
        public int getCircuitBreakerFailureThreshold() { return circuitBreakerFailureThreshold; }
        public void setCircuitBreakerFailureThreshold(int value) { this.circuitBreakerFailureThreshold = value; }
        public long getCircuitBreakerOpenMillis() { return circuitBreakerOpenMillis; }
        public void setCircuitBreakerOpenMillis(long value) { this.circuitBreakerOpenMillis = value; }
    }

    public static class Reranker {
        private boolean enabled;
        private String baseUrl = "http://localhost:8082";
        private String path = "/rerank";
        private String apiKey = "";
        private String model = "BAAI/bge-reranker-v2-m3";
        private int candidateTopK = 20;
        private int maxFragmentsPerLogicalEvidence = 2;
        private int maxFragmentsTotal = 40;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
        public String getPath() { return path; }
        public void setPath(String path) { this.path = path; }
        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }
        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }
        public int getCandidateTopK() { return candidateTopK; }
        public void setCandidateTopK(int candidateTopK) { this.candidateTopK = candidateTopK; }
        public int getMaxFragmentsPerLogicalEvidence() { return maxFragmentsPerLogicalEvidence; }
        public void setMaxFragmentsPerLogicalEvidence(int value) { this.maxFragmentsPerLogicalEvidence = value; }
        public int getMaxFragmentsTotal() { return maxFragmentsTotal; }
        public void setMaxFragmentsTotal(int value) { this.maxFragmentsTotal = value; }
    }

    public static class RerankerConnection {
        private String label = "";
        private String baseUrl = "";
        private String apiKey = "";
        private String healthPath = "/v1/models";
        private int connectTimeoutMillis = 2000;
        private int readTimeoutMillis = 5000;

        public String getLabel() { return label; }
        public void setLabel(String label) { this.label = label; }
        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }
        public String getHealthPath() { return healthPath; }
        public void setHealthPath(String healthPath) { this.healthPath = healthPath; }
        public int getConnectTimeoutMillis() { return connectTimeoutMillis; }
        public void setConnectTimeoutMillis(int connectTimeoutMillis) { this.connectTimeoutMillis = connectTimeoutMillis; }
        public int getReadTimeoutMillis() { return readTimeoutMillis; }
        public void setReadTimeoutMillis(int readTimeoutMillis) { this.readTimeoutMillis = readTimeoutMillis; }
    }
}
