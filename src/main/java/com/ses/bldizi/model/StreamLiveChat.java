package com.ses.bldizi.model;

import java.sql.Timestamp;
import java.util.UUID;

public class StreamLiveChat {
    private UUID id;
    private UUID streamLiveId;
    private UUID userId;
    private String nickname;
    private String message;
    private Timestamp sentAt;
    private boolean isVerified;

    public StreamLiveChat() {
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

    public Timestamp getSentAt() { return sentAt; }
    public void setSentAt(Timestamp sentAt) { this.sentAt = sentAt; }

    public boolean isVerified() { return isVerified; }
    public void setVerified(boolean verified) { isVerified = verified; }
}
