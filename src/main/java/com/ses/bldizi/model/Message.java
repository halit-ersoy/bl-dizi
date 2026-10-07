package com.ses.bldizi.model;

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
public class Message {
    private UUID id;
    private UUID senderId;
    private UUID receiverId;
    private String content;
    private Instant timestamp;
    private boolean isRead;
    private boolean isDelivered;

    // Additional fields for UI convenience
    private String senderNickname;
    private String receiverNickname;
    private String senderRole;
    private String receiverRole;
    private boolean senderVerified;
    private boolean receiverVerified;
}
