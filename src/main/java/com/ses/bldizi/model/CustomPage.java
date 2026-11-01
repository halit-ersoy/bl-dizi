package com.ses.bldizi.model;

import java.time.Instant;
import java.util.UUID;

public class CustomPage {
    private UUID id;
    private String title;
    private String slug;
    private String entryFile;
    private String targetSite;
    private long totalSize;
    private int fileCount;
    private boolean active;
    private Instant createdAt;
    private Instant updatedAt;

    public CustomPage() {
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getSlug() {
        return slug;
    }

    public void setSlug(String slug) {
        this.slug = slug;
    }

    public String getEntryFile() {
        return entryFile;
    }

    public void setEntryFile(String entryFile) {
        this.entryFile = entryFile;
    }

    public String getTargetSite() {
        return targetSite;
    }

    public void setTargetSite(String targetSite) {
        this.targetSite = targetSite;
    }

    public long getTotalSize() {
        return totalSize;
    }

    public void setTotalSize(long totalSize) {
        this.totalSize = totalSize;
    }

    public int getFileCount() {
        return fileCount;
    }

    public void setFileCount(int fileCount) {
        this.fileCount = fileCount;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
