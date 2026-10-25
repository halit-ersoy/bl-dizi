package com.ses.bldizi.model;

import java.time.Instant;
import java.util.UUID;

public class PushPreferences {
    private UUID userId;
    private boolean allowMessagePush = true;
    private boolean allowCommentPush = true;
    private boolean allowMessagePreview = false;
    private Instant updatedAt;

    public PushPreferences() {
    }

    public PushPreferences(UUID userId) {
        this.userId = userId;
        this.allowMessagePush = true;
        this.allowCommentPush = true;
        this.allowMessagePreview = false;
        this.updatedAt = Instant.now();
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public boolean isAllowMessagePush() {
        return allowMessagePush;
    }

    public void setAllowMessagePush(boolean allowMessagePush) {
        this.allowMessagePush = allowMessagePush;
    }

    public boolean isAllowCommentPush() {
        return allowCommentPush;
    }

    public void setAllowCommentPush(boolean allowCommentPush) {
        this.allowCommentPush = allowCommentPush;
    }

    public boolean isAllowMessagePreview() {
        return allowMessagePreview;
    }

    public void setAllowMessagePreview(boolean allowMessagePreview) {
        this.allowMessagePreview = allowMessagePreview;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
