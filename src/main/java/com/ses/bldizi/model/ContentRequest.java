package com.ses.bldizi.model;

import java.time.Instant;
import java.util.UUID;

public class ContentRequest {
    private UUID id;
    private UUID userId;
    private String userNickname;
    private String title;
    private String contentType; // "movie" or "series"
    private String posterUrl;
    private Integer releaseYear;
    private Integer tmdbId;
    private Integer tvmazeId;
    private String description;
    private String status; // "PENDING", "APPROVED", "COMPLETED", "REJECTED"
    private int voteCount;
    private Instant createdAt;
    private boolean userVoted;
    private boolean isBl = true;
    private String platform = "bldizi";

    public ContentRequest() {
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public String getUserNickname() {
        return userNickname;
    }

    public void setUserNickname(String userNickname) {
        this.userNickname = userNickname;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public String getPosterUrl() {
        return posterUrl;
    }

    public void setPosterUrl(String posterUrl) {
        this.posterUrl = posterUrl;
    }

    public Integer getReleaseYear() {
        return releaseYear;
    }

    public void setReleaseYear(Integer releaseYear) {
        this.releaseYear = releaseYear;
    }

    public Integer getTmdbId() {
        return tmdbId;
    }

    public void setTmdbId(Integer tmdbId) {
        this.tmdbId = tmdbId;
    }

    public Integer getTvmazeId() {
        return tvmazeId;
    }

    public void setTvmazeId(Integer tvmazeId) {
        this.tvmazeId = tvmazeId;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public int getVoteCount() {
        return voteCount;
    }

    public void setVoteCount(int voteCount) {
        this.voteCount = voteCount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public boolean isUserVoted() {
        return userVoted;
    }

    public void setUserVoted(boolean userVoted) {
        this.userVoted = userVoted;
    }

    public boolean isBl() {
        return isBl;
    }

    public void setBl(boolean bl) {
        isBl = bl;
    }

    public String getPlatform() {
        return platform;
    }

    public void setPlatform(String platform) {
        this.platform = platform;
    }
}
