package com.ses.bldizi.model;

import java.util.UUID;

public class VideoSource {
    private UUID id;
    private UUID contentId;
    private String sourceName;
    private String sourceUrl;
    private int sortOrder;

    public VideoSource() {
    }

    public VideoSource(UUID id, UUID contentId, String sourceName, String sourceUrl, int sortOrder) {
        this.id = id;
        this.contentId = contentId;
        this.sourceName = sourceName;
        this.sourceUrl = sourceUrl;
        this.sortOrder = sortOrder;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getContentId() {
        return contentId;
    }

    public void setContentId(UUID contentId) {
        this.contentId = contentId;
    }

    public String getSourceName() {
        return sourceName;
    }

    public void setSourceName(String sourceName) {
        this.sourceName = sourceName;
    }

    public String getSourceUrl() {
        return sourceUrl;
    }

    public void setSourceUrl(String sourceUrl) {
        this.sourceUrl = sourceUrl;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }
}
