package com.ses.bldizi.controller;

import com.ses.bldizi.model.Message;
import com.ses.bldizi.model.Person;
import com.ses.bldizi.repository.MessageRepository;
import com.ses.bldizi.repository.PersonRepository;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@RestController
public class MessageController {

    private final MessageRepository messageRepository;
    private final PersonRepository personRepository;

    @Autowired
    public MessageController(MessageRepository messageRepository, PersonRepository personRepository) {
        this.messageRepository = messageRepository;
        this.personRepository = personRepository;
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

    @GetMapping("/messages")
    public ResponseEntity<Resource> getMessagesPage() {
        try {
            Resource htmlPage = new ClassPathResource("static/homepage/html/messages.html");
            if (!htmlPage.exists()) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_HTML_VALUE)
                    .body(htmlPage);
        } catch (Exception e) {
            return ResponseEntity.status(500).build();
        }
    }

    @PostMapping("/api/messages/send")
    public ResponseEntity<?> sendMessage(
            @CookieValue(name = "wdiAuth", required = false) String cookie,
            @RequestBody Map<String, String> request,
            HttpServletRequest httpRequest) {

        String token = resolveCookie(cookie, httpRequest);
        if (token == null) {
            return ResponseEntity.status(401).body(Map.of("success", false, "message", "Oturum açmanız gerekiyor."));
        }

        try {
            Map<String, Object> senderInfo = personRepository.getUserInfoByCookie(token);
            UUID senderId = UUID.fromString(senderInfo.get("ID").toString());

            String receiverNickname = request.get("receiverNickname");
            String content = request.get("content");

            if (content == null || content.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Mesaj boş olamaz."));
            }

            if (content.length() > 400) {
                return ResponseEntity.badRequest()
                        .body(Map.of("success", false, "message", "Mesaj en fazla 400 karakter olabilir."));
            }

            Optional<Person> receiver = personRepository.findByNicknameOrEmail(receiverNickname);
            if (receiver.isEmpty()) {
                return ResponseEntity.status(404).body(Map.of("success", false, "message", "Kullanıcı bulunamadı."));
            }

            if (!receiver.get().isAllowMessages()) {
                return ResponseEntity.status(403)
                        .body(Map.of("success", false, "message", "Bu kullanıcı mesaj alımını kapatmış."));
            }

            if (messageRepository.isBlocked(senderId, receiver.get().getId())) {
                return ResponseEntity.status(403)
                        .body(Map.of("success", false, "message",
                                "Bu kullanıcı ile mesajlaşamazsınız (Engelleme mevcut)."));
            }

            messageRepository.sendMessage(senderId, receiver.get().getId(), content);

            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @GetMapping("/api/messages/conversations")
    public ResponseEntity<?> getConversations(
            @CookieValue(name = "wdiAuth", required = false) String cookie,
            HttpServletRequest httpRequest) {
        String token = resolveCookie(cookie, httpRequest);
        if (token == null) {
            return ResponseEntity.status(401).body(Map.of("success", false, "message", "Unauthorized"));
        }

        try {
            Map<String, Object> userInfo = personRepository.getUserInfoByCookie(token);
            UUID userId = UUID.fromString(userInfo.get("ID").toString());

            List<Message> conversations = messageRepository.getConversationList(userId);

            // Mark all unread messages for this user as delivered when they check their list
            messageRepository.markAsDelivered(userId);

            return ResponseEntity.ok(conversations);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @GetMapping("/api/messages/history/{nickname}")
    public ResponseEntity<?> getChatHistory(
            @CookieValue(name = "wdiAuth", required = false) String cookie,
            @PathVariable("nickname") String otherNickname,
            HttpServletRequest httpRequest) {

        String token = resolveCookie(cookie, httpRequest);
        if (token == null) {
            return ResponseEntity.status(401).body(Map.of("success", false, "message", "Unauthorized"));
        }

        try {
            Map<String, Object> userInfo = personRepository.getUserInfoByCookie(token);
            UUID userId = UUID.fromString(userInfo.get("ID").toString());

            Optional<Person> otherUser = personRepository.findByNicknameOrEmail(otherNickname);
            if (otherUser.isEmpty()) {
                return ResponseEntity.status(404).body(Map.of("success", false, "message", "User not found"));
            }

            List<Message> history = messageRepository.getChatHistory(userId, otherUser.get().getId());

            // Mark as delivered when opening the chat
            messageRepository.markAsDelivered(userId);
            // Mark as read when opening the chat
            messageRepository.markAsRead(otherUser.get().getId(), userId);

            return ResponseEntity.ok(history);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @PostMapping("/api/user/settings/toggle-messages")
    public ResponseEntity<?> toggleMessages(
            @CookieValue(name = "wdiAuth", required = false) String cookie,
            @RequestBody(required = false) Map<String, Boolean> requestBody,
            HttpServletRequest httpRequest) {

        String token = resolveCookie(cookie, httpRequest);
        if (token == null) {
            return ResponseEntity.status(401).body(Map.of("success", false, "Result", false, "message", "Unauthorized"));
        }

        Boolean allow = requestBody != null ? requestBody.get("allow") : null;
        if (allow == null)
            allow = true;

        try {
            personRepository.updateAllowMessagesByCookie(token, allow);
            return ResponseEntity.ok(Map.of("success", true, "Result", true, "allow", allow));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("success", false, "Result", false, "message", e.getMessage()));
        }
    }

    @GetMapping("/api/users/search-messaging")
    public ResponseEntity<?> searchUsersForMessaging(
            @CookieValue(name = "wdiAuth", required = false) String cookie,
            @RequestParam("q") String query,
            HttpServletRequest httpRequest) {

        String token = resolveCookie(cookie, httpRequest);
        if (token == null) {
            return ResponseEntity.status(401).body(Map.of("success", false, "message", "Unauthorized"));
        }

        try {
            Map<String, Object> userInfo = personRepository.getUserInfoByCookie(token);
            String myNickname = userInfo.get("nickname").toString();

            List<Person> results = personRepository.searchUsersForMessaging(query, myNickname);
            return ResponseEntity.ok(results);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @PostMapping("/api/messages/block")
    public ResponseEntity<?> blockUser(
            @CookieValue(name = "wdiAuth", required = false) String cookie,
            @RequestBody Map<String, String> request,
            HttpServletRequest httpRequest) {

        String token = resolveCookie(cookie, httpRequest);
        if (token == null)
            return ResponseEntity.status(401).build();
        String nickname = request.get("nickname");
        if (nickname == null)
            return ResponseEntity.badRequest().build();

        try {
            Map<String, Object> userInfo = personRepository.getUserInfoByCookie(token);
            UUID myId = UUID.fromString(userInfo.get("ID").toString());

            Optional<Person> them = personRepository.findByNicknameOrEmail(nickname);
            if (them.isEmpty())
                return ResponseEntity.notFound().build();

            messageRepository.blockUser(myId, them.get().getId());
            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @PostMapping("/api/messages/unblock")
    public ResponseEntity<?> unblockUser(
            @CookieValue(name = "wdiAuth", required = false) String cookie,
            @RequestBody Map<String, String> request,
            HttpServletRequest httpRequest) {

        String token = resolveCookie(cookie, httpRequest);
        if (token == null)
            return ResponseEntity.status(401).build();
        String nickname = request.get("nickname");
        if (nickname == null)
            return ResponseEntity.badRequest().build();

        try {
            Map<String, Object> userInfo = personRepository.getUserInfoByCookie(token);
            UUID myId = UUID.fromString(userInfo.get("ID").toString());

            Optional<Person> them = personRepository.findByNicknameOrEmail(nickname);
            if (them.isEmpty())
                return ResponseEntity.notFound().build();

            messageRepository.unblockUser(myId, them.get().getId());
            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @GetMapping("/api/messages/block-status/{nickname}")
    public ResponseEntity<?> getBlockStatus(
            @CookieValue(name = "wdiAuth", required = false) String cookie,
            @PathVariable("nickname") String nickname,
            HttpServletRequest httpRequest) {

        String token = resolveCookie(cookie, httpRequest);
        if (token == null)
            return ResponseEntity.status(401).build();

        try {
            Map<String, Object> userInfo = personRepository.getUserInfoByCookie(token);
            UUID myId = UUID.fromString(userInfo.get("ID").toString());

            Optional<Person> them = personRepository.findByNicknameOrEmail(nickname);
            if (them.isEmpty())
                return ResponseEntity.notFound().build();

            boolean blockedByMe = messageRepository.isBlockedByMe(myId, them.get().getId());
            boolean blockedByThem = messageRepository.isBlockedByMe(them.get().getId(), myId);

            return ResponseEntity.ok(Map.of(
                    "blockedByMe", blockedByMe,
                    "blockedByThem", blockedByThem));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @PostMapping("/api/user/report")
    public ResponseEntity<?> reportUser(
            @CookieValue(name = "wdiAuth", required = false) String cookie,
            @RequestBody Map<String, String> request,
            HttpServletRequest httpRequest) {

        String token = resolveCookie(cookie, httpRequest);
        if (token == null)
            return ResponseEntity.status(401).build();
        String nickname = request.get("nickname");
        String reason = request.get("reason");
        String context = request.get("context");

        if (nickname == null || reason == null)
            return ResponseEntity.badRequest().build();

        try {
            Map<String, Object> userInfo = personRepository.getUserInfoByCookie(token);
            UUID myId = UUID.fromString(userInfo.get("ID").toString());

            Optional<Person> them = personRepository.findByNicknameOrEmail(nickname);
            if (them.isEmpty())
                return ResponseEntity.notFound().build();

            messageRepository.reportUser(myId, them.get().getId(), reason, context);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("success", false, "message", e.getMessage()));
        }
    }
}
