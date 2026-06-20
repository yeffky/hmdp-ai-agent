package com.hmdp.rag.model;

/**
 * 知识库统计信息
 */
public class KnowledgeBaseStats {

    private String collectionName;
    private long totalPoints;

    public KnowledgeBaseStats() {}

    public KnowledgeBaseStats(String collectionName, long totalPoints) {
        this.collectionName = collectionName;
        this.totalPoints = totalPoints;
    }

    public String getCollectionName() { return collectionName; }
    public void setCollectionName(String collectionName) { this.collectionName = collectionName; }

    public long getTotalPoints() { return totalPoints; }
    public void setTotalPoints(long totalPoints) { this.totalPoints = totalPoints; }
}
