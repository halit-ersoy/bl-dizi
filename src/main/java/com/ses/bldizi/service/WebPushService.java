package com.ses.bldizi.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ses.bldizi.config.WebPushConfig;
import com.ses.bldizi.exception.PushSubscriptionExpiredException;
import com.ses.bldizi.model.PushPayloadDto;
import com.ses.bldizi.model.PushPreferences;
import com.ses.bldizi.model.PushSubscription;
import com.ses.bldizi.repository.PushSubscriptionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class WebPushService {

    private static final Logger logger = LoggerFactory.getLogger(WebPushService.class);

    private final WebPushConfig webPushConfig;
    private final WebPushClientService webPushClientService;
    private final PushSubscriptionRepository pushSubscriptionRepository;
    private final ObjectMapper objectMapper;

    public WebPushService(
            WebPushConfig webPushConfig,
            WebPushClientService webPushClientService,
            PushSubscriptionRepository pushSubscriptionRepository,
            ObjectMapper objectMapper) {
        this.webPushConfig = webPushConfig;
        this.webPushClientService = webPushClientService;
        this.pushSubscriptionRepository = pushSubscriptionRepository;
        this.objectMapper = objectMapper;
    }

    public String getVapidPublicKey() {
        return webPushConfig.getPublicKey();
    }

    public boolean isEnabled() {
        return webPushConfig.isEnabled();
    }

    /**
     * Sends push notifications asynchronously to all active devices of the recipient,
     * respecting the user's notification preferences (DMs, comments, preview opt-in).
     */
    @Async
    public void sendPushAsync(String receiverNickname, String title, String body, String url, String tag) {
        if (!isEnabled()) {
            logger.debug("Web Push is disabled or VAPID keys are missing. Skipping notification to {}", receiverNickname);
            return;
        }

        if (receiverNickname == null || receiverNickname.isBlank()) {
            return;
        }

        try {
            // 1. Check user preferences
            PushPreferences prefs = pushSubscriptionRepository.getPreferencesByNickname(receiverNickname);
            boolean isDirectMessage = tag != null && tag.startsWith("msg_");
            boolean isCommentReply = tag != null && (tag.startsWith("reply_") || tag.startsWith("comment_"));

            if (isDirectMessage && !prefs.isAllowMessagePush()) {
                logger.debug("Direct message push suppressed for user {} by preference.", receiverNickname);
                return;
            }

            if (isCommentReply && !prefs.isAllowCommentPush()) {
                logger.debug("Comment reply push suppressed for user {} by preference.", receiverNickname);
                return;
            }

            String displayBody = body != null ? body : "";
            if (isDirectMessage && !prefs.isAllowMessagePreview()) {
                displayBody = "Yeni bir mesajınız var.";
            }

            // 2. Query active subscriptions
            List<PushSubscription> subscriptions = pushSubscriptionRepository.findActiveSubscriptionsByNickname(receiverNickname);
            if (subscriptions == null || subscriptions.isEmpty()) {
                logger.debug("No active push subscriptions found for user {}", receiverNickname);
                return;
            }

            // 3. Construct payload
            PushPayloadDto payload = new PushPayloadDto();
            payload.setTitle(title != null ? title : "BL Dizi");
            payload.setBody(displayBody);
            payload.setUrl(url != null ? url : "/");
            payload.setTag(tag != null ? tag : ("bldizi_" + System.currentTimeMillis()));
            payload.setIcon("/elements/img/bldizi-logo-notif.png");
            payload.setBadge("/elements/img/bldizi-logo-notif.png");
            payload.setTimestamp(System.currentTimeMillis());

            if (isDirectMessage) {
                payload.setData(Map.of("type", "DIRECT_MESSAGE", "sender", title != null ? title : ""));
            } else if (isCommentReply) {
                payload.setData(Map.of("type", "COMMENT_REPLY", "url", url != null ? url : "/"));
            }

            String payloadJson = objectMapper.writeValueAsString(payload);

            // 4. Dispatch to each subscription
            for (PushSubscription sub : subscriptions) {
                try {
                    webPushClientService.sendPush(sub, payloadJson);
                    logger.debug("Successfully sent Web Push to user {}, sub ID {}", receiverNickname, sub.getId());
                } catch (PushSubscriptionExpiredException expiredEx) {
                    logger.info("Push subscription expired for user {}, deactivating sub ID: {}", receiverNickname, sub.getId());
                    pushSubscriptionRepository.deactivateSubscriptionById(sub.getId());
                } catch (Exception ex) {
                    logger.warn("Failed to send Web Push to sub ID {}: {}", sub.getId(), ex.getMessage());
                }
            }
        } catch (Exception e) {
            logger.error("Error in sendPushAsync for user {}: {}", receiverNickname, e.getMessage(), e);
        }
    }
}
