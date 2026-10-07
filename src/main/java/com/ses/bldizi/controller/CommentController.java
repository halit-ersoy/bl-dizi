package com.ses.bldizi.controller;

import com.ses.bldizi.model.CommentViewModel;
import com.ses.bldizi.repository.CommentRepository;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/video")
public class CommentController {

    private final CommentRepository commentRepository;

    public CommentController(CommentRepository commentRepository) {
        this.commentRepository = commentRepository;
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
