package com.ses.bldizi.model;

import java.util.UUID;

public class StreamLiveStatusDto {
    private UUID id;
    private String title;
    private String description;
    private String contentType;
    private UUID contentId;
    private String contentName;
    private String slug;
    private String posterUrl;
    private String videoUrl;
    private long scheduledStartTime;   // Epoch millis
    private int durationMinutes;
    private int durationSeconds;
    private long endTime;              // Epoch millis
    private long serverTime;            // Current server epoch millis
    private long liveEdgeSeconds;       // Current live broadcast position in seconds
    private long remainingStartSeconds; // Seconds until start (0 if already started)
    private String status;             // "UPCOMING", "LIVE", "ENDED", "CLOSED"
    private boolean active;
    private boolean chatOpen;           // Chat only open 10 mins before start until 1 hr after end
    private long chatOpenInSeconds;    // Seconds remaining until chat opens (0 if already open)
    private int viewerCount;           // Real-time viewer count
    private String platform = "BLDIZI";

    public StreamLiveStatusDto() {
    }

    public StreamLiveStatusDto(UUID id, String title, String description, String contentType, UUID contentId,
                               String contentName, String slug, String posterUrl, String videoUrl,
                               long scheduledStartTime, int durationMinutes, int durationSeconds,
                               long endTime, long serverTime, long liveEdgeSeconds,
                               long remainingStartSeconds, String status, boolean active,
                               boolean chatOpen, long chatOpenInSeconds) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.contentType = contentType;
        this.contentId = contentId;
        this.contentName = contentName;
        this.slug = slug;
        this.posterUrl = posterUrl;
        this.videoUrl = videoUrl;
        this.scheduledStartTime = scheduledStartTime;
        this.durationMinutes = durationMinutes;
        this.durationSeconds = durationSeconds;
        this.endTime = endTime;
        this.serverTime = serverTime;
        this.liveEdgeSeconds = liveEdgeSeconds;
        this.remainingStartSeconds = remainingStartSeconds;
        this.status = status;
        this.active = active;
        this.chatOpen = chatOpen;
        this.chatOpenInSeconds = chatOpenInSeconds;
    }

    public StreamLiveStatusDto(UUID id, String title, String description, String contentType, UUID contentId,
                               String contentName, String slug, String posterUrl, String videoUrl,
                               long scheduledStartTime, int durationMinutes, long endTime, long serverTime,
                               long liveEdgeSeconds, long remainingStartSeconds, String status, boolean active,
                               boolean chatOpen, long chatOpenInSeconds) {
        this(id, title, description, contentType, contentId, contentName, slug, posterUrl, videoUrl,
             scheduledStartTime, durationMinutes, durationMinutes * 60, endTime, serverTime,
             liveEdgeSeconds, remainingStartSeconds, status, active, chatOpen, chatOpenInSeconds);
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

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public UUID getContentId() {
        return contentId;
    }

    public void setContentId(UUID contentId) {
        this.contentId = contentId;
    }

    public String getContentName() {
        return contentName;
    }

    public void setContentName(String contentName) {
        this.contentName = contentName;
    }

    public String getSlug() {
        return slug;
    }

    public void setSlug(String slug) {
        this.slug = slug;
    }

    public String getPosterUrl() {
        return posterUrl;
    }

    public void setPosterUrl(String posterUrl) {
        this.posterUrl = posterUrl;
    }

    public String getVideoUrl() {
        return videoUrl;
    }

    public void setVideoUrl(String videoUrl) {
        this.videoUrl = videoUrl;
    }

    public long getScheduledStartTime() {
        return scheduledStartTime;
    }

    public void setScheduledStartTime(long scheduledStartTime) {
        this.scheduledStartTime = scheduledStartTime;
    }

    public int getDurationMinutes() {
        return durationMinutes;
    }

    public void setDurationMinutes(int durationMinutes) {
        this.durationMinutes = durationMinutes;
    }

    public int getDurationSeconds() {
        return durationSeconds;
    }

    public void setDurationSeconds(int durationSeconds) {
        this.durationSeconds = durationSeconds;
    }

    public long getEndTime() {
        return endTime;
    }

    public void setEndTime(long endTime) {
        this.endTime = endTime;
    }

    public long getServerTime() {
        return serverTime;
    }

    public void setServerTime(long serverTime) {
        this.serverTime = serverTime;
    }

    public long getLiveEdgeSeconds() {
        return liveEdgeSeconds;
    }

    public void setLiveEdgeSeconds(long liveEdgeSeconds) {
        this.liveEdgeSeconds = liveEdgeSeconds;
    }

    public long getRemainingStartSeconds() {
        return remainingStartSeconds;
    }

    public void setRemainingStartSeconds(long remainingStartSeconds) {
        this.remainingStartSeconds = remainingStartSeconds;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public boolean isChatOpen() {
        return chatOpen;
    }

    public void setChatOpen(boolean chatOpen) {
        this.chatOpen = chatOpen;
    }

    public long getChatOpenInSeconds() {
        return chatOpenInSeconds;
    }

    public void setChatOpenInSeconds(long chatOpenInSeconds) {
        this.chatOpenInSeconds = chatOpenInSeconds;
    }

    public int getViewerCount() {
        return viewerCount;
    }

    public void setViewerCount(int viewerCount) {
        this.viewerCount = viewerCount;
    }

    public String getPlatform() {
        return platform != null && !platform.isBlank() ? platform : "WDI";
    }

    public void setPlatform(String platform) {
        this.platform = platform;
    }
}
