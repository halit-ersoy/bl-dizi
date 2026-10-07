package com.ses.bldizi.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Notification {
    private UUID id;
    private String title;
    private String message;
    private UUID contentId;
    private String type; // 'Movie' or 'SoapOpera'
    private Instant createdAt;
    
    @JsonProperty("isRead")
    private boolean isRead;
    private String slug; // Used for UI routing

    public Notification(String title, String message, UUID contentId, String type) {
        this.id = UUID.randomUUID();
        this.title = title;
        this.message = message;
        this.contentId = contentId;
        this.type = type;
        this.createdAt = Instant.now();
        this.isRead = false;
    }
}
