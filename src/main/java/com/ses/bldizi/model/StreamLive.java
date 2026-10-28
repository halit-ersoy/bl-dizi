package com.ses.bldizi.model;

import java.sql.Timestamp;
import java.util.UUID;

public class StreamLive {
    private UUID id;
    private String title;
    private String description;
    private String contentType;
    private UUID contentId;
    private String contentName;
    private String slug;
    private String posterUrl;
    private String videoUrl;
    private Timestamp scheduledStartTime;
    private int durationMinutes;
    private int durationSeconds;
    private Timestamp endTime;
    private boolean isActive;
    private Timestamp createdAt;
    private String platform = "BLDIZI";

    public StreamLive() {
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }

    public UUID getContentId() { return contentId; }
    public void setContentId(UUID contentId) { this.contentId = contentId; }

    public String getContentName() { return contentName; }
    public void setContentName(String contentName) { this.contentName = contentName; }

    public String getSlug() { return slug; }
    public void setSlug(String slug) { this.slug = slug; }

    public String getPosterUrl() { return posterUrl; }
    public void setPosterUrl(String posterUrl) { this.posterUrl = posterUrl; }

    public String getVideoUrl() { return videoUrl; }
    public void setVideoUrl(String videoUrl) { this.videoUrl = videoUrl; }

    public Timestamp getScheduledStartTime() { return scheduledStartTime; }
    public void setScheduledStartTime(Timestamp scheduledStartTime) { this.scheduledStartTime = scheduledStartTime; }

    public int getDurationMinutes() { return durationMinutes; }
    public void setDurationMinutes(int durationMinutes) { this.durationMinutes = durationMinutes; }

    public int getDurationSeconds() { return durationSeconds; }
    public void setDurationSeconds(int durationSeconds) { this.durationSeconds = durationSeconds; }

    public Timestamp getEndTime() { return endTime; }
    public void setEndTime(Timestamp endTime) { this.endTime = endTime; }

    public boolean isActive() { return isActive; }
    public void setActive(boolean active) { isActive = active; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }

    public String getPlatform() { return platform; }
    public void setPlatform(String platform) { this.platform = platform; }
}
