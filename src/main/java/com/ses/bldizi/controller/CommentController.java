package com.ses.bldizi.controller;

import com.ses.bldizi.model.CommentViewModel;
import com.ses.bldizi.repository.CommentRepository;
import com.ses.bldizi.service.NotificationService;
import com.ses.bldizi.service.WebPushService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/video")
public class CommentController {

    private final CommentRepository commentRepository;
    private final NotificationService notificationService;
    private final SimpMessagingTemplate messagingTemplate;
    private final WebPushService webPushService;

    public CommentController(CommentRepository commentRepository,
                             NotificationService notificationService,
                             SimpMessagingTemplate messagingTemplate,
                             WebPushService webPushService) {
        this.webPushService = webPushService;
        this.commentRepository = commentRepository;
        this.notificationService = notificationService;
        this.messagingTemplate = messagingTemplate;
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

    @GetMapping("/comments")
    public ResponseEntity<List<CommentViewModel>> getComments(
            @RequestParam("id") UUID id,
            @CookieValue(value = "wdiAuth", required = false) String cookie,
            HttpServletRequest request) {
        try {
            String token = resolveCookie(cookie, request);
            return ResponseEntity.ok(commentRepository.getComments(id, token));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PostMapping("/comment")
    public ResponseEntity<Void> addComment(
            @RequestParam("id") UUID id,
            @RequestParam(value = "spoiler", defaultValue = "false") boolean spoiler,
            @RequestParam(value = "parentId", required = false) UUID parentId,
            @RequestBody String text,
            @CookieValue(name = "wdiAuth", required = false) String cookie,
            HttpServletRequest request) {

        String token = resolveCookie(cookie, request);
        if (token == null || token.trim().isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        try {
            commentRepository.addComment(id, token, text, spoiler, parentId);

            // If this is a reply to another comment, notify the original author
            if (parentId != null) {
                try {
                    Map<String, Object> replyData = commentRepository.getReplyNotificationData(parentId, token, id != null ? id.toString() : null);
                    if (replyData != null) {
                        Object parentAuthorIdObj = replyData.get("ParentAuthorId");
                        Object replierIdObj = replyData.get("ReplierId");
                        String parentNickname = (String) replyData.get("ParentAuthorNickname");
                        String replierNickname = (String) replyData.get("ReplierNickname");
                        String contentName = (String) replyData.get("ContentName");
                        String contentSlug = (String) replyData.get("ContentSlug");
                        Object contentIdObj = replyData.get("ContentId");

                        UUID parentAuthorId = parentAuthorIdObj instanceof UUID ? (UUID) parentAuthorIdObj :
                                (parentAuthorIdObj != null ? UUID.fromString(parentAuthorIdObj.toString()) : null);
                        UUID replierId = replierIdObj instanceof UUID ? (UUID) replierIdObj :
                                (replierIdObj != null ? UUID.fromString(replierIdObj.toString()) : null);
                        UUID contentId = contentIdObj instanceof UUID ? (UUID) contentIdObj :
                                (contentIdObj != null ? UUID.fromString(contentIdObj.toString()) : null);

                        if (parentAuthorId != null && !parentAuthorId.equals(replierId) && parentNickname != null) {
                            String title = (replierNickname != null ? replierNickname : "Biri") + " yorumunuza yanıt verdi";
                            String cleanText = (text != null ? text.trim() : "");
                            String snippet = cleanText.length() > 80 ? cleanText.substring(0, 77) + "..." : cleanText;
                            String message = (contentName != null && !contentName.isEmpty() ? "[" + contentName + "] " : "") + snippet;

                            // Save to persistent database notifications
                            notificationService.createNotification(title, message, contentId, "CommentReply", parentAuthorId, contentSlug);

                            // Send real-time WebSocket notification to author's channel
                            Map<String, Object> wsPayload = new HashMap<>();
                            wsPayload.put("type", "COMMENT_REPLY");
                            wsPayload.put("title", title);
                            wsPayload.put("message", message);
                            wsPayload.put("replierNickname", replierNickname);
                            wsPayload.put("contentName", contentName);
                            wsPayload.put("contentSlug", contentSlug);
                            wsPayload.put("text", snippet);
                            wsPayload.put("url", "/" + contentSlug + "#comments");

                                                        messagingTemplate.convertAndSend("/topic/notifications/" + parentNickname, wsPayload);

                            // Send background Web Push notification if author has site closed
                            webPushService.sendPushAsync(parentNickname, title, message,
                                    "/" + contentSlug + "#comments", "reply_" + System.currentTimeMillis());
                        }
                    }
                } catch (Exception notifEx) {
                    System.err.println("Failed to dispatch comment reply notification in bldizi: " + notifEx.getMessage());
                }
            }

            return ResponseEntity.ok().build();
        } catch (RuntimeException e) {
            if (e.getMessage() != null && e.getMessage().contains("Unauthorized or Banned")) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PostMapping("/comment/like")
    public ResponseEntity<Void> likeComment(
            @RequestParam("commentId") UUID commentId,
            @CookieValue(name = "wdiAuth", required = false) String cookie,
            HttpServletRequest request) {

        String token = resolveCookie(cookie, request);
        if (token == null || token.trim().isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        try {
            commentRepository.likeComment(commentId, token);
            return ResponseEntity.ok().build();
        } catch (RuntimeException e) {
            if (e.getMessage() != null && e.getMessage().contains("Unauthorized or Banned")) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @DeleteMapping("/comment")
    public ResponseEntity<Void> deleteComment(
            @RequestParam("commentId") UUID commentId,
            @CookieValue(name = "wdiAuth", required = false) String cookie,
            HttpServletRequest request) {

        String token = resolveCookie(cookie, request);
        if (token == null || token.trim().isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        try {
            boolean deleted = commentRepository.deleteComment(commentId, token);
            if (!deleted) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
