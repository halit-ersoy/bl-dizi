package com.ses.bldizi.controller;

import com.ses.bldizi.repository.PersonRepository;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
public class UserInteractionController {

    private final PersonRepository personRepository;

    public UserInteractionController(PersonRepository personRepository) {
        this.personRepository = personRepository;
    }

    private String resolveCookie(String cookie, HttpServletRequest request) {
        if (cookie != null && !cookie.trim().isEmpty()) {
            return cookie.trim();
        }
        if (request != null) {
            // Check request cookies
            Cookie[] cookies = request.getCookies();
            if (cookies != null) {
                for (Cookie c : cookies) {
                    if ("wdiAuth".equalsIgnoreCase(c.getName()) && c.getValue() != null && !c.getValue().isEmpty()) {
                        return c.getValue().trim();
                    }
                }
            }
            // Check Authorization header: Bearer <token>
            String authHeader = request.getHeader("Authorization");
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                return authHeader.substring(7).trim();
            }
            // Check X-Auth-Token header
            String xAuth = request.getHeader("X-Auth-Token");
            if (xAuth != null && !xAuth.trim().isEmpty()) {
                return xAuth.trim();
            }
        }
        return null;
    }

    @GetMapping("/api/user/profile")
    public ResponseEntity<?> getProfile(@CookieValue(name = "wdiAuth", required = false) String cookie,
                                        HttpServletRequest request) {
        String token = resolveCookie(cookie, request);
        if (token == null) {
            return ResponseEntity.status(401).body(Map.of("success", false, "message", "Oturum açılmamış."));
        }

        try {
            Map<String, Object> userInfo = personRepository.getUserInfoByCookie(token);
            if (userInfo == null || userInfo.isEmpty()) {
                return ResponseEntity.status(401).body(Map.of("success", false, "message", "Kullanıcı bulunamadı."));
            }
            return ResponseEntity.ok(userInfo);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @PostMapping("/api/user/update-profile")
    public ResponseEntity<?> updateProfile(@CookieValue(name = "wdiAuth", required = false) String cookie,
                                           @RequestBody Map<String, String> requestData,
                                           HttpServletRequest request) {
        String token = resolveCookie(cookie, request);
        if (token == null) {
            return ResponseEntity.status(401).body(Map.of("success", false, "message", "Unauthorized"));
        }

        String nickname = requestData.get("nickname");
        String name = requestData.get("name");
        String surname = requestData.get("surname");
        String email = requestData.get("email");

        if (nickname == null || nickname.trim().isEmpty() ||
                name == null || name.trim().isEmpty() ||
                surname == null || surname.trim().isEmpty() ||
                email == null || email.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Lütfen tüm zorunlu alanları doldurunuz."));
        }

        Map<String, Object> result = personRepository.updateProfileByCookie(token, nickname.trim(), name.trim(), surname.trim(), email.trim());
        return ResponseEntity.ok(result);
    }

    @PostMapping("/api/user/update-password")
    public ResponseEntity<?> updatePassword(@CookieValue(name = "wdiAuth", required = false) String cookie,
                                            @RequestBody Map<String, String> requestData,
                                            HttpServletRequest request) {
        String token = resolveCookie(cookie, request);
        if (token == null) {
            return ResponseEntity.status(401).body(Map.of("success", false, "message", "Unauthorized"));
        }

        String newPassword = requestData.get("newPassword");
        if (newPassword == null || newPassword.length() < 6) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Şifre en az 6 karakter olmalıdır."));
        }

        Map<String, Object> result = personRepository.updatePasswordByCookie(token, newPassword);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/api/notifications")
    public ResponseEntity<List<Object>> getNotifications() {
        return ResponseEntity.ok(new ArrayList<>());
    }

    @GetMapping("/api/notifications/unread-count")
    public ResponseEntity<Map<String, Integer>> getUnreadCount() {
        return ResponseEntity.ok(Map.of("count", 0));
    }

    @PostMapping("/api/feedback")
    public ResponseEntity<?> submitFeedback(@RequestBody(required = false) Map<String, String> request) {
        return ResponseEntity.ok(Map.of("success", true, "message", "Geri bildiriminiz alındı."));
    }
}
