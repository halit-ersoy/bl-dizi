package com.ses.bldizi.controller;

import com.ses.bldizi.repository.WatchHistoryRepository;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/history")
@SuppressWarnings({"SqlResolve", "SqlNoDataSourceInspection"})
public class WatchHistoryController {

    private final WatchHistoryRepository watchHistoryRepository;
    private final JdbcTemplate jdbcTemplate;

    public WatchHistoryController(WatchHistoryRepository watchHistoryRepository, JdbcTemplate jdbcTemplate) {
        this.watchHistoryRepository = watchHistoryRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    private String resolveCookie(String cookie, HttpServletRequest request) {
        if (cookie != null && !cookie.trim().isEmpty()) {
            return cookie.trim();
        }
        if (request != null) {
            Cookie[] cookies = request.getCookies();
            if (cookies != null) {
                for (Cookie c : cookies) {
                    if ("wdiAuth".equalsIgnoreCase(c.getName()) && c.getValue() != null && !c.getValue().isEmpty()) {
                        return c.getValue().trim();
                    }
                }
            }
            String authHeader = request.getHeader("Authorization");
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                return authHeader.substring(7).trim();
            }
            String xAuth = request.getHeader("X-Auth-Token");
            if (xAuth != null && !xAuth.trim().isEmpty()) {
                return xAuth.trim();
            }
        }
        return null;
    }

    private UUID resolveUserId(String cookie, HttpServletRequest request) {
        String token = resolveCookie(cookie, request);
        if (token == null || token.isBlank()) {
            return null;
        }
        try {
            try {
                UUID possibleId = UUID.fromString(token);
                Integer count = jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM Person WHERE ID = ?", Integer.class, possibleId.toString());
                if (count != null && count > 0) {
                    return possibleId;
                }
            } catch (Exception ignored) {
            }

            String sql = "SELECT ID FROM Person WHERE cookie = ?";
            String idStr = jdbcTemplate.queryForObject(sql, String.class, token);
            return idStr != null ? UUID.fromString(idStr) : null;
        } catch (Exception e) {
            return null;
        }
    }

    @GetMapping
    public ResponseEntity<?> getWatchHistory(
            @CookieValue(name = "wdiAuth", required = false) String cookie,
            HttpServletRequest request) {
        UUID userId = resolveUserId(cookie, request);
        if (userId == null) {
            return ResponseEntity.status(401).body(Map.of("success", false, "message", "Oturum açınız."));
        }

        List<Map<String, Object>> history = watchHistoryRepository.getUserWatchHistory(userId);
        return ResponseEntity.ok(history);
    }

    @PostMapping("/record")
    public ResponseEntity<?> recordWatch(
            @RequestParam("contentId") UUID contentId,
            @CookieValue(name = "wdiAuth", required = false) String cookie,
            HttpServletRequest request) {
        UUID userId = resolveUserId(cookie, request);
        if (userId == null) {
            // Unauthenticated user: not an error, return ok without recording
            return ResponseEntity.ok(Map.of("success", false, "message", "Misafir kullanıcı"));
        }

        watchHistoryRepository.recordWatch(userId, contentId);
        return ResponseEntity.ok(Map.of("success", true));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteHistoryItem(
            @PathVariable("id") UUID id,
            @CookieValue(name = "wdiAuth", required = false) String cookie,
            HttpServletRequest request) {
        UUID userId = resolveUserId(cookie, request);
        if (userId == null) {
            return ResponseEntity.status(401).body(Map.of("success", false, "message", "Yetkisiz"));
        }

        boolean deleted = watchHistoryRepository.deleteHistoryItem(userId, id);
        return ResponseEntity.ok(Map.of("success", deleted));
    }

    @DeleteMapping("/clear")
    public ResponseEntity<?> clearWatchHistory(
            @CookieValue(name = "wdiAuth", required = false) String cookie,
            HttpServletRequest request) {
        UUID userId = resolveUserId(cookie, request);
        if (userId == null) {
            return ResponseEntity.status(401).body(Map.of("success", false, "message", "Yetkisiz"));
        }

        boolean cleared = watchHistoryRepository.clearUserHistory(userId);
        return ResponseEntity.ok(Map.of("success", cleared));
    }
}
