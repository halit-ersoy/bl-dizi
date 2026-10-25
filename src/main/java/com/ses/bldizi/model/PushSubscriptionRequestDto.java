package com.ses.bldizi.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class PushSubscriptionRequestDto {
    private String endpoint;
    private KeysDto keys;
    private String p256dh;
    private String auth;
    private String userAgent;
    private String nickname;

    public PushSubscriptionRequestDto() {
    }

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public KeysDto getKeys() {
        return keys;
    }

    public void setKeys(KeysDto keys) {
        this.keys = keys;
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

    public String getUserAgent() {
        return userAgent;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public String resolveP256dh() {
        if (keys != null && keys.getP256dh() != null && !keys.getP256dh().isBlank()) {
            return keys.getP256dh().trim();
        }
        return p256dh != null ? p256dh.trim() : "";
    }

    public String resolveAuth() {
        if (keys != null && keys.getAuth() != null && !keys.getAuth().isBlank()) {
            return keys.getAuth().trim();
        }
        return auth != null ? auth.trim() : "";
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class KeysDto {
        private String p256dh;
        private String auth;

        public KeysDto() {
        }

        public KeysDto(String p256dh, String auth) {
            this.p256dh = p256dh;
            this.auth = auth;
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
    }
}
