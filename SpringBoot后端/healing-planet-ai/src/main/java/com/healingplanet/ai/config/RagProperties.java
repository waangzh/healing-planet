package com.healingplanet.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;

@ConfigurationProperties(prefix = "app.rag")
public class RagProperties {

    private int denseTopK = 30;
    private int sparseTopK = 30;
    private int finalTopK = 6;
    private double similarityThreshold = 0.25;
    private String internalApiKey = "";
    private Path dataDirectory = Path.of("data", "rag");
    private final Qdrant qdrant = new Qdrant();
    private final EntityResolution entityResolution = new EntityResolution();
    private final Reranker reranker = new Reranker();
    private final PlantState plantState = new PlantState();
    private final DiseaseDetector diseaseDetector = new DiseaseDetector();
    private final Attachments attachments = new Attachments();
    private final Eval eval = new Eval();

    public int getDenseTopK() { return denseTopK; }
    public void setDenseTopK(int denseTopK) { this.denseTopK = denseTopK; }
    public int getSparseTopK() { return sparseTopK; }
    public void setSparseTopK(int sparseTopK) { this.sparseTopK = sparseTopK; }
    public int getFinalTopK() { return finalTopK; }
    public void setFinalTopK(int finalTopK) { this.finalTopK = finalTopK; }
    public double getSimilarityThreshold() { return similarityThreshold; }
    public void setSimilarityThreshold(double similarityThreshold) { this.similarityThreshold = similarityThreshold; }
    public String getInternalApiKey() { return internalApiKey; }
    public void setInternalApiKey(String internalApiKey) { this.internalApiKey = internalApiKey; }
    public Path getDataDirectory() { return dataDirectory; }
    public void setDataDirectory(Path dataDirectory) { this.dataDirectory = dataDirectory; }
    public Qdrant getQdrant() { return qdrant; }
    public EntityResolution getEntityResolution() { return entityResolution; }
    public Reranker getReranker() { return reranker; }
    public PlantState getPlantState() { return plantState; }
    public DiseaseDetector getDiseaseDetector() { return diseaseDetector; }
    public Attachments getAttachments() { return attachments; }
    public Eval getEval() { return eval; }

    public static class Eval {
        private Path fixtureDirectory = Path.of("..", "..", "rag-eval", "fixtures");
        private Instant clockInstant = Instant.parse("2026-08-17T02:00:00Z");
        private ZoneId clockZone = ZoneId.of("Asia/Shanghai");

        public Path getFixtureDirectory() { return fixtureDirectory; }
        public void setFixtureDirectory(Path fixtureDirectory) { this.fixtureDirectory = fixtureDirectory; }
        public Instant getClockInstant() { return clockInstant; }
        public void setClockInstant(Instant clockInstant) { this.clockInstant = clockInstant; }
        public ZoneId getClockZone() { return clockZone; }
        public void setClockZone(ZoneId clockZone) { this.clockZone = clockZone; }
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
        private double vectorAcceptanceThreshold = 0.82;
        private double strongVectorThreshold = 0.88;
        private double marginThreshold = 0.08;
        private double lexicalAcceptanceThreshold = 0.90;
        private double characterCorroborationThreshold = 0.45;

        public int getCandidateTopK() { return candidateTopK; }
        public void setCandidateTopK(int candidateTopK) { this.candidateTopK = candidateTopK; }
        public double getVectorAcceptanceThreshold() { return vectorAcceptanceThreshold; }
        public void setVectorAcceptanceThreshold(double value) { this.vectorAcceptanceThreshold = value; }
        public double getStrongVectorThreshold() { return strongVectorThreshold; }
        public void setStrongVectorThreshold(double value) { this.strongVectorThreshold = value; }
        public double getMarginThreshold() { return marginThreshold; }
        public void setMarginThreshold(double marginThreshold) { this.marginThreshold = marginThreshold; }
        public double getLexicalAcceptanceThreshold() { return lexicalAcceptanceThreshold; }
        public void setLexicalAcceptanceThreshold(double value) { this.lexicalAcceptanceThreshold = value; }
        public double getCharacterCorroborationThreshold() { return characterCorroborationThreshold; }
        public void setCharacterCorroborationThreshold(double value) { this.characterCorroborationThreshold = value; }
    }

    public static class Reranker {
        private boolean enabled;
        private String baseUrl = "http://localhost:8082";
        private String path = "/rerank";
        private String apiKey = "";
        private String model = "BAAI/bge-reranker-v2-m3";

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
    }
}
