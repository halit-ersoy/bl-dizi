package com.ses.bldizi.controller;

import com.ses.bldizi.repository.FeedbackRepository;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
public class FeedbackController {

    private final FeedbackRepository feedbackRepository;

    @Autowired
    public FeedbackController(FeedbackRepository feedbackRepository) {
        this.feedbackRepository = feedbackRepository;
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

    @PostMapping("/api/feedback")
    public ResponseEntity<?> submitFeedback(
            @CookieValue(name = "wdiAuth", required = false) String cookie,
            @RequestBody Map<String, String> request,
            HttpServletRequest httpRequest) {

        String token = resolveCookie(cookie, httpRequest);
        if (token == null || token.isBlank()) {
            return ResponseEntity.status(401).body(Map.of(
                    "success", false,
                    "message", "Bu işlemi yapmak için oturum açmanız gerekmektedir."));
        }

        if (request == null) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Lütfen formu doldurunuz."));
        }

        String subject = request.get("subject");
        String message = request.get("message");

        if (subject == null || subject.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Konu alanı boş bırakılamaz."));
        }

        if (message == null || message.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Mesaj alanı boş bırakılamaz."));
        }

        try {
            Map<String, Object> result = feedbackRepository.submitFeedback(token, subject.trim(), message.trim());
            boolean success = Boolean.TRUE.equals(result.get("success"));
            if (success) {
                return ResponseEntity.ok(result);
            } else {
                return ResponseEntity.status(400).body(result);
            }
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "message", "Sunucu hatası: " + e.getMessage()));
        }
    }
}
