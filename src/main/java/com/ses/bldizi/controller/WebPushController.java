package com.ses.bldizi.controller;

import com.ses.bldizi.config.WebPushConfig;
import com.ses.bldizi.model.Person;
import com.ses.bldizi.model.PushPreferences;
import com.ses.bldizi.model.PushPreferencesDto;
import com.ses.bldizi.model.PushSubscriptionRequestDto;
import com.ses.bldizi.repository.PersonRepository;
import com.ses.bldizi.repository.PushSubscriptionRepository;
import com.ses.bldizi.service.WebPushClientService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@RestController
public class WebPushController {

    private final WebPushConfig webPushConfig;
    private final PushSubscriptionRepository pushSubscriptionRepository;
    private final PersonRepository personRepository;
    private final WebPushClientService webPushClientService;

    public WebPushController(
            WebPushConfig webPushConfig,
            PushSubscriptionRepository pushSubscriptionRepository,
            PersonRepository personRepository,
            WebPushClientService webPushClientService) {
        this.webPushConfig = webPushConfig;
        this.pushSubscriptionRepository = pushSubscriptionRepository;
        this.personRepository = personRepository;
        this.webPushClientService = webPushClientService;
    }

    private UUID resolveUserId(String wdiCookie, String bldiziCookie, HttpServletRequest request) {
        String cookie = (bldiziCookie != null && !bldiziCookie.isBlank()) ? bldiziCookie : wdiCookie;
        if ((cookie == null || cookie.isBlank()) && request != null && request.getCookies() != null) {
            for (Cookie c : request.getCookies()) {
                if ("bldiziAuth".equalsIgnoreCase(c.getName()) || "wdiAuth".equalsIgnoreCase(c.getName())) {
                    cookie = c.getValue();
                    break;
                }
            }
        }
        if (cookie != null && !cookie.isBlank()) {
            return personRepository.findUserIdByCookie(cookie);
        }
        return null;
    }

    @GetMapping("/api/push/public-key")
    public ResponseEntity<?> getPublicKey() {
        return ResponseEntity.ok(Map.of(
                "publicKey", webPushConfig.getPublicKey(),
                "enabled", webPushConfig.isEnabled()
        ));
    }

    @PostMapping("/api/push/subscribe")
    public ResponseEntity<?> subscribe(
            @CookieValue(name = "wdiAuth", required = false) String wdiCookie,
            @CookieValue(name = "bldiziAuth", required = false) String bldiziCookie,
            @RequestHeader(value = "User-Agent", required = false) String headerUserAgent,
            @RequestBody PushSubscriptionRequestDto request,
            HttpServletRequest httpRequest) {

        UUID userId = resolveUserId(wdiCookie, bldiziCookie, httpRequest);
        if (userId == null && request != null && request.getNickname() != null && !request.getNickname().isBlank()) {
            Optional<Person> p = personRepository.findByNicknameOrEmail(request.getNickname());
            if (p.isPresent()) {
                userId = p.get().getId();
            }
        }

        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("success", false, "message", "Abonelik kaydı için oturum açmalısınız."));
        }

        String p256dh = request != null ? request.resolveP256dh() : "";
        String auth = request != null ? request.resolveAuth() : "";

        if (request == null || request.getEndpoint() == null || request.getEndpoint().isBlank()
                || p256dh.isBlank() || auth.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", "Eksik abonelik bilgileri (endpoint, p256dh, auth)."));
        }

        try {
            webPushClientService.validateEndpoint(request.getEndpoint());

            String userAgent = request.getUserAgent() != null ? request.getUserAgent() : headerUserAgent;
            if (userAgent != null && userAgent.length() > 500) {
                userAgent = userAgent.substring(0, 500);
            }

            pushSubscriptionRepository.saveOrUpdateSubscription(
                    userId,
                    request.getEndpoint().trim(),
                    p256dh,
                    auth,
                    userAgent
            );

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Push bildirimi aboneliği başarıyla kaydedildi."
            ));
        } catch (SecurityException se) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", se.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "message", "Abonelik kaydedilemedi: " + e.getMessage()));
        }
    }

    @PostMapping("/api/push/unsubscribe")
    public ResponseEntity<?> unsubscribe(
            @CookieValue(name = "wdiAuth", required = false) String wdiCookie,
            @CookieValue(name = "bldiziAuth", required = false) String bldiziCookie,
            @RequestBody Map<String, String> request,
            HttpServletRequest httpRequest) {

        UUID userId = resolveUserId(wdiCookie, bldiziCookie, httpRequest);
        String endpoint = request != null ? request.get("endpoint") : null;
        if (endpoint == null || endpoint.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", "Endpoint parametresi gerekli."));
        }

        try {
            boolean removed = false;
            if (userId != null) {
                removed = pushSubscriptionRepository.deactivateSubscriptionByEndpoint(userId, endpoint.trim());
            } else {
                pushSubscriptionRepository.deleteByEndpoint(endpoint.trim());
                removed = true;
            }
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "removed", removed,
                    "message", "Abonelik bu cihaz için başarıyla kapatıldı."
            ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "message", "Abonelik kapatılamadı: " + e.getMessage()));
        }
    }

    @GetMapping("/api/push/preferences")
    public ResponseEntity<?> getPreferences(
            @CookieValue(name = "wdiAuth", required = false) String wdiCookie,
            @CookieValue(name = "bldiziAuth", required = false) String bldiziCookie,
            HttpServletRequest httpRequest) {

        UUID userId = resolveUserId(wdiCookie, bldiziCookie, httpRequest);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("success", false, "message", "Oturum açmanız gerekiyor."));
        }

        PushPreferences prefs = pushSubscriptionRepository.getPreferences(userId);
        return ResponseEntity.ok(new PushPreferencesDto(
                prefs.isAllowMessagePush(),
                prefs.isAllowCommentPush(),
                prefs.isAllowMessagePreview()
        ));
    }

    @PostMapping("/api/push/preferences")
    public ResponseEntity<?> savePreferences(
            @CookieValue(name = "wdiAuth", required = false) String wdiCookie,
            @CookieValue(name = "bldiziAuth", required = false) String bldiziCookie,
            @RequestBody PushPreferencesDto dto,
            HttpServletRequest httpRequest) {

        UUID userId = resolveUserId(wdiCookie, bldiziCookie, httpRequest);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("success", false, "message", "Oturum açmanız gerekiyor."));
        }

        if (dto == null) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Tercih verisi eksik."));
        }

        PushPreferences existing = pushSubscriptionRepository.getPreferences(userId);
        boolean allowMessage = dto.resolveAllowMessagePush(existing.isAllowMessagePush());
        boolean allowComment = dto.resolveAllowCommentPush(existing.isAllowCommentPush());
        boolean allowPreview = dto.resolveAllowMessagePreview(existing.isAllowMessagePreview());

        pushSubscriptionRepository.savePreferences(
                userId,
                allowMessage,
                allowComment,
                allowPreview
        );

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Bildirim tercihleri güncellendi.",
                "preferences", new PushPreferencesDto(allowMessage, allowComment, allowPreview)
        ));
    }
}
