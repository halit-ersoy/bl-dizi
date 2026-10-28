package com.ses.bldizi.model;

import java.util.UUID;

public class StreamLiveChatMessageDto {
    private UUID id;
    private UUID streamLiveId;
    private UUID userId;
    private String nickname;
    private String message;
    private long sentAtMillis;
    private boolean isVerified;

    public StreamLiveChatMessageDto() {
    }

    public StreamLiveChatMessageDto(UUID id, UUID streamLiveId, UUID userId, String nickname, String message, long sentAtMillis, boolean isVerified) {
        this.id = id;
        this.streamLiveId = streamLiveId;
        this.userId = userId;
        this.nickname = nickname;
        this.message = message;
        this.sentAtMillis = sentAtMillis;
        this.isVerified = isVerified;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getStreamLiveId() { return streamLiveId; }
    public void setStreamLiveId(UUID streamLiveId) { this.streamLiveId = streamLiveId; }

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }

    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public long getSentAtMillis() { return sentAtMillis; }
    public void setSentAtMillis(long sentAtMillis) { this.sentAtMillis = sentAtMillis; }

    public long getSentAt() { return sentAtMillis; }
    public void setSentAt(long sentAt) { this.sentAtMillis = sentAt; }

    public boolean isVerified() { return isVerified; }
    public void setVerified(boolean verified) { this.isVerified = verified; }

    public boolean getVerified() { return isVerified; }
    public void setIsVerified(boolean isVerified) { this.isVerified = isVerified; }
}