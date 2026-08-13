package com.healingplanet.ai.domain;

public record IndexReport(int plantDocuments, int communityDocuments, int diseaseDocuments, int deletedDocuments) {
    public static IndexReport plant(int count, int deleted) { return new IndexReport(count, 0, 0, deleted); }
    public static IndexReport community(int count, int deleted) { return new IndexReport(0, count, 0, deleted); }
    public static IndexReport disease(int count, int deleted) { return new IndexReport(0, 0, count, deleted); }
}
