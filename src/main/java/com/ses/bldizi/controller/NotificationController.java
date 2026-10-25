package com.ses.bldizi.controller;

import com.ses.bldizi.model.Notification;
import com.ses.bldizi.service.NotificationService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {
    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    private String resolveCookie(String cookie, HttpServletRequest request) {
        if (cookie != null && !cookie.trim().isEmpty()) {
            return cookie.trim();
        }
        if (request != null && request.getCookies() != null) {
            for (Cookie c : request.getCookies()) {
                if ("wdiAuth".equals(c.getName()) && c.getValue() != null && !c.getValue().trim().isEmpty()) {
                    return c.getValue().trim();
                }
            }
        }
        return null;
    }

    @GetMapping
    public ResponseEntity<List<Notification>> getNotifications(
            @RequestParam(value = "limit", defaultValue = "10") int limit,
            @CookieValue(value = "wdiAuth", required = false) String cookie,
            HttpServletRequest request) {
        String token = resolveCookie(cookie, request);
        UUID userId = notificationService.resolveUserIdByCookie(token);
        return ResponseEntity.ok(notificationService.getRecentNotifications(limit, userId));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<Map<String, Integer>> getUnreadCount(
            @CookieValue(value = "wdiAuth", required = false) String cookie,
            HttpServletRequest request) {
        String token = resolveCookie(cookie, request);
        UUID userId = notificationService.resolveUserIdByCookie(token);
        return ResponseEntity.ok(Map.of("count", notificationService.getUnreadCount(userId)));
    }

    @PostMapping("/{id}/read")
    public ResponseEntity<Void> markAsRead(@PathVariable("id") UUID id) {
        notificationService.markAsRead(id);
        return ResponseEntity.ok().build();
    }
}
