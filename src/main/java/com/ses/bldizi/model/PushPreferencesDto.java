package com.ses.bldizi.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;

public class PushPreferencesDto {

    @JsonProperty("allowMessagePush")
    @JsonAlias({"allowDirectMessagePush"})
    private Boolean allowMessagePush;

    @JsonProperty("allowCommentPush")
    @JsonAlias({"allowCommentReplyPush"})
    private Boolean allowCommentPush;

    @JsonProperty("allowMessagePreview")
    @JsonAlias({"showContentPreview"})
    private Boolean allowMessagePreview;

    public PushPreferencesDto() {
    }

    public PushPreferencesDto(boolean allowMessagePush, boolean allowCommentPush, boolean allowMessagePreview) {
        this.allowMessagePush = allowMessagePush;
        this.allowCommentPush = allowCommentPush;
        this.allowMessagePreview = allowMessagePreview;
    }

    public boolean resolveAllowMessagePush(boolean fallback) {
        return allowMessagePush != null ? allowMessagePush : fallback;
    }

    public boolean resolveAllowCommentPush(boolean fallback) {
        return allowCommentPush != null ? allowCommentPush : fallback;
    }

    public boolean resolveAllowMessagePreview(boolean fallback) {
        return allowMessagePreview != null ? allowMessagePreview : fallback;
    }

    public boolean isAllowMessagePush() {
        return allowMessagePush != null && allowMessagePush;
    }

    public Boolean getAllowMessagePush() {
        return allowMessagePush;
    }

    public void setAllowMessagePush(Boolean allowMessagePush) {
        this.allowMessagePush = allowMessagePush;
    }

    @JsonProperty("allowDirectMessagePush")
    public Boolean getAllowDirectMessagePush() {
        return allowMessagePush;
    }

    public void setAllowDirectMessagePush(Boolean allowDirectMessagePush) {
        if (this.allowMessagePush == null) {
            this.allowMessagePush = allowDirectMessagePush;
        }
    }

    public boolean isAllowCommentPush() {
        return allowCommentPush != null && allowCommentPush;
    }

    public Boolean getAllowCommentPush() {
        return allowCommentPush;
    }

    public void setAllowCommentPush(Boolean allowCommentPush) {
        this.allowCommentPush = allowCommentPush;
    }

    @JsonProperty("allowCommentReplyPush")
    public Boolean getAllowCommentReplyPush() {
        return allowCommentPush;
    }

    public void setAllowCommentReplyPush(Boolean allowCommentReplyPush) {
        if (this.allowCommentPush == null) {
            this.allowCommentPush = allowCommentReplyPush;
        }
    }

    public boolean isAllowMessagePreview() {
        return allowMessagePreview != null && allowMessagePreview;
    }

    public Boolean getAllowMessagePreview() {
        return allowMessagePreview;
    }

    public void setAllowMessagePreview(Boolean allowMessagePreview) {
        this.allowMessagePreview = allowMessagePreview;
    }

    @JsonProperty("showContentPreview")
    public Boolean getShowContentPreview() {
        return allowMessagePreview;
    }

    public void setShowContentPreview(Boolean showContentPreview) {
        if (this.allowMessagePreview == null) {
            this.allowMessagePreview = showContentPreview;
        }
    }
}
