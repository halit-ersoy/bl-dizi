package com.ses.bldizi.repository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Repository
public class FeedbackRepository {

    private final JdbcTemplate jdbcTemplate;

    @Autowired
    public FeedbackRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Resolves the PersonID from the wdiAuth cookie value, then inserts a feedback
     * record into the shared WhoDatIdols Feedback table.
     * Enforces character limit (400) and daily feedback limit (300).
     */
    public Map<String, Object> submitFeedback(String cookie, String subject, String message) {
        Map<String, Object> response = new HashMap<>();
        try {
            // 1. Character Limit Check (Backend enforcement)
            if (message != null && message.length() > 400) {
                response.put("success", false);
                response.put("message", "Mesajınız 400 karakterden uzun olamaz.");
                return response;
            }

            // 2. Daily Total Limit Check (300 per day total system-wide)
            if (getDailyFeedbackCount() >= 300) {
                response.put("success", false);
                response.put("message", "Bugünlük geri bildirim limitine ulaşıldı. Lütfen yarın tekrar deneyin.");
                return response;
            }

            UUID cookieUUID = UUID.fromString(cookie);

            // Resolve PersonID from cookie
            String findUserSql = "SELECT ID FROM [Person] WHERE cookie = ?";
            UUID userId;
            try {
                userId = jdbcTemplate.queryForObject(findUserSql,
                        (rs, rowNum) -> UUID.fromString(rs.getString("ID")),
                        cookieUUID.toString());
            } catch (Exception e) {
                response.put("success", false);
                response.put("message", "Geçersiz oturum. Lütfen tekrar giriş yapın.");
                return response;
            }

            if (userId == null) {
                response.put("success", false);
                response.put("message", "Kullanıcı bulunamadı.");
                return response;
            }

            // Insert feedback into shared table
            String insertSql = "INSERT INTO [Feedback] (UserID, Subject, Message, CreatedAt) VALUES (?, ?, ?, ?)";
            jdbcTemplate.update(insertSql, userId.toString(), subject, message, java.sql.Timestamp.from(Instant.now()));

            response.put("success", true);
            response.put("message", "Geri bildiriminiz başarıyla gönderildi. Teşekkür ederiz!");
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Bir hata oluştu: " + e.getMessage());
        }
        return response;
    }

    private int getDailyFeedbackCount() {
        String sql = "SELECT COUNT(*) FROM [Feedback] WHERE CAST(CreatedAt AS DATE) = CAST(GETUTCDATE() AS DATE)";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class);
        return count != null ? count : 0;
    }

    /**
     * Returns all feedbacks joined with Person info, newest first.
     */
    public List<Map<String, Object>> getAllFeedbacks() {
        String sql = """
                SELECT f.ID, p.nickname, p.email, f.Subject, f.Message, f.CreatedAt
                FROM [Feedback] f
                INNER JOIN [Person] p ON f.UserID = p.ID
                ORDER BY f.CreatedAt DESC
                """;
        return jdbcTemplate.queryForList(sql);
    }

    /**
     * Deletes a feedback record by ID.
     */
    public void deleteFeedback(UUID feedbackId) {
        String sql = "DELETE FROM [Feedback] WHERE ID = ?";
        jdbcTemplate.update(sql, feedbackId.toString());
    }
}
