package com.ses.bldizi.controller;

import com.ses.bldizi.model.PushSubscriptionModel;
import com.ses.bldizi.repository.PersonRepository;
import com.ses.bldizi.repository.PushSubscriptionRepository;
import com.ses.bldizi.service.WebPushService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/push")
public class PushNotificationController {

    private static final Logger logger = LoggerFactory.getLogger(PushNotificationController.class);

    private final WebPushService webPushService;
    private final PushSubscriptionRepository subscriptionRepository;
    private final PersonRepository personRepository;

    public PushNotificationController(WebPushService webPushService,
                                      PushSubscriptionRepository subscriptionRepository,
                                      PersonRepository personRepository) {
        this.webPushService = webPushService;
        this.subscriptionRepository = subscriptionRepository;
        this.personRepository = personRepository;
    }

    @GetMapping("/public-key")
    public ResponseEntity<Map<String, String>> getPublicKey() {
        return ResponseEntity.ok(Map.of("publicKey", webPushService.getVapidPublicKey()));
    }

    @PostMapping("/subscribe")
    public ResponseEntity<?> subscribe(
            @CookieValue(name = "wdiAuth", required = false) String wdiCookie,
            @CookieValue(name = "bldiziAuth", required = false) String bldiziCookie,
            @RequestBody Map<String, Object> payload) {

        String cookie = wdiCookie != null ? wdiCookie : bldiziCookie;
        String nickname = null;

        if (cookie != null) {
            try {
                Map<String, Object> userInfo = personRepository.getUserInfoByCookie(cookie);
                if (userInfo != null) {
                    for (Map.Entry<String, Object> entry : userInfo.entrySet()) {
                        if ("nickname".equalsIgnoreCase(entry.getKey()) && entry.getValue() != null) {
                            nickname = entry.getValue().toString();
                            break;
                        }
                    }
                }
            } catch (Exception e) {
                logger.warn("Could not extract user info from cookie: {}", e.getMessage());
            }
        }

        // Fallback to nickname in payload if available
        if ((nickname == null || nickname.trim().isEmpty()) && payload.containsKey("nickname")) {
            Object nickObj = payload.get("nickname");
            if (nickObj != null && !nickObj.toString().trim().isEmpty() && !"null".equalsIgnoreCase(nickObj.toString())) {
                nickname = nickObj.toString().trim();
            }
        }

        if (nickname == null || nickname.trim().isEmpty()) {
            logger.warn("Push subscription rejected: No user session or nickname provided. Payload: {}", payload);
            return ResponseEntity.status(401).body(Map.of("success", false, "message", "Kullanıcı oturumu bulunamadı"));
        }

        String endpoint = (String) payload.get("endpoint");
        @SuppressWarnings("unchecked")
        Map<String, String> keys = (Map<String, String>) payload.get("keys");

        if (endpoint == null || keys == null || !keys.containsKey("p256dh") || !keys.containsKey("auth")) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Geçersiz abonelik verisi"));
        }

        String p256dh = keys.get("p256dh");
        String auth = keys.get("auth");

        subscriptionRepository.saveOrUpdate(nickname, endpoint, p256dh, auth);
        logger.info("Successfully registered Web Push subscription for user: '{}', endpoint: {}", nickname, endpoint);

        return ResponseEntity.ok(Map.of("success", true, "message", "Push aboneliği başarıyla kaydedildi"));
    }

    @PostMapping("/unsubscribe")
    public ResponseEntity<?> unsubscribe(@RequestBody Map<String, String> payload) {
        String endpoint = payload.get("endpoint");
        if (endpoint != null && !endpoint.isEmpty()) {
            subscriptionRepository.deleteByEndpoint(endpoint);
            logger.info("Unsubscribed push endpoint: {}", endpoint);
        }
        return ResponseEntity.ok(Map.of("success", true));
    }

    @GetMapping("/subscriptions-count")
    public ResponseEntity<?> getSubscriptionsCount(
            @CookieValue(name = "wdiAuth", required = false) String cookie,
            @RequestParam(name = "nickname", required = false) String paramNickname) {
        String nickname = paramNickname;
        if (nickname == null && cookie != null) {
            try {
                Map<String, Object> userInfo = personRepository.getUserInfoByCookie(cookie);
                if (userInfo != null) {
                    for (Map.Entry<String, Object> entry : userInfo.entrySet()) {
                        if ("nickname".equalsIgnoreCase(entry.getKey()) && entry.getValue() != null) {
                            nickname = entry.getValue().toString();
                            break;
                        }
                    }
                }
            } catch (Exception ignored) {}
        }

        if (nickname == null) {
            return ResponseEntity.ok(Map.of("count", 0, "nickname", "unknown"));
        }

        List<PushSubscriptionModel> subs = subscriptionRepository.findByNickname(nickname);
        return ResponseEntity.ok(Map.of("count", subs.size(), "nickname", nickname));
    }
}
