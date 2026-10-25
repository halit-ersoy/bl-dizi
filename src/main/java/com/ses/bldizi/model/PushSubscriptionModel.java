package com.ses.bldizi.model;

import java.time.LocalDateTime;

public class PushSubscriptionModel {
    private Long id;
    private String userNickname;
    private String endpoint;
    private String p256dh;
    private String auth;
    private LocalDateTime createdAt;

    public PushSubscriptionModel() {
    }

    public PushSubscriptionModel(Long id, String userNickname, String endpoint, String p256dh, String auth, LocalDateTime createdAt) {
        this.id = id;
        this.userNickname = userNickname;
        this.endpoint = endpoint;
        this.p256dh = p256dh;
        this.auth = auth;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUserNickname() {
        return userNickname;
    }

    public void setUserNickname(String userNickname) {
        this.userNickname = userNickname;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public String getP256dh() {
        return p256dh;
    }

    public void setP256dh(String p256dh) {
        this.p256dh = p256dh;
    }

    public String getAuth() {
        return auth;
    }

    public void setAuth(String auth) {
        this.auth = auth;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
