package com.ses.bldizi.model;

import java.util.Map;

public class PushPayloadDto {
    private String title;
    private String body;
    private String url;
    private String tag;
    private String icon;
    private String badge;
    private Long timestamp;
    private Map<String, Object> data;

    public PushPayloadDto() {
    }

    public PushPayloadDto(String title, String body, String url, String tag) {
        this.title = title;
        this.body = body;
        this.url = url;
        this.tag = tag;
        this.icon = "/favicon.svg";
        this.badge = "/favicon.svg";
        this.timestamp = System.currentTimeMillis();
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getTag() {
        return tag;
    }

    public void setTag(String tag) {
        this.tag = tag;
    }

    public String getIcon() {
        return icon;
    }

    public void setIcon(String icon) {
        this.icon = icon;
    }

    public String getBadge() {
        return badge;
    }

    public void setBadge(String badge) {
        this.badge = badge;
    }

    public Long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Long timestamp) {
        this.timestamp = timestamp;
    }

    public Map<String, Object> getData() {
        return data;
    }

    public void setData(Map<String, Object> data) {
        this.data = data;
    }
}
