package com.ses.bldizi.repository;

import com.ses.bldizi.model.CommentViewModel;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.*;

@Repository
public class CommentRepository {
    private final JdbcTemplate jdbcTemplate;

    public CommentRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<CommentViewModel> getComments(UUID contentId, String cookie) {
        String sql = """
            SELECT
                c.ID, c.Text as comment, c.CreatedAt as date, c.Spoiler,
                COALESCE(p.Nickname, c.Nickname) as nickname,
                c.UserId as authorId,
                c.LikeCount,
                c.ParentId,
                c.ContentId,
                CASE WHEN cl.UserId IS NOT NULL THEN 1 ELSE 0 END as isLiked,
                CASE WHEN c.UserId = p_me.ID THEN 1 ELSE 0 END as isAuthor,
                c.IsApproved,
                p.Role,
                p.isVerified
            FROM Comments c
            LEFT JOIN Person p ON c.UserId = p.ID
            LEFT JOIN Person p_me ON p_me.cookie = ?
            LEFT JOIN CommentLikes cl ON c.ID = cl.CommentId AND cl.UserId = p_me.ID
            WHERE c.ContentId = ? AND (c.IsApproved = 1 OR c.UserId = p_me.ID)
            ORDER BY c.CreatedAt DESC
        """;

        List<CommentViewModel> allComments = jdbcTemplate.query(sql, new CommentRowMapper(),
                cookie,
                contentId.toString());

        return buildCommentTree(allComments);
    }

    private List<CommentViewModel> buildCommentTree(List<CommentViewModel> flatComments) {
        Map<UUID, CommentViewModel> map = new HashMap<>();
        List<CommentViewModel> roots = new ArrayList<>();

        for (CommentViewModel c : flatComments) {
            map.put(c.getId(), c);
            if (c.getReplies() == null) {
                c.setReplies(new ArrayList<>());
            }
        }

        for (CommentViewModel c : flatComments) {
            if (c.getParentId() != null && map.containsKey(c.getParentId())) {
                map.get(c.getParentId()).getReplies().add(c);
            } else {
                roots.add(c);
            }
        }

        return roots;
    }

    public void addComment(UUID contentId, String cookie, String text, boolean spoiler, UUID parentId) {
        String sql = """
            INSERT INTO Comments (ContentId, UserId, Text, Spoiler, Nickname, ParentId, IsApproved, CreatedAt)
            SELECT ?, ID, ?, ?, Nickname, ?, CASE WHEN Role IN ('ADMIN', 'SUPER_ADMIN', 'KURUCU', 'GELISTIRICI') THEN 1 ELSE 0 END, ?
            FROM Person WHERE cookie = ? AND isBanned = 0
        """;
        int affected = jdbcTemplate.update(sql,
                contentId.toString(),
                text,
                spoiler,
                parentId != null ? parentId.toString() : null,
                java.sql.Timestamp.from(Instant.now()),
                cookie);

        if (affected == 0) {
            throw new RuntimeException("Unauthorized or Banned");
        }
    }

    public void likeComment(UUID commentId, String cookie) {
        String userSql = "SELECT ID FROM Person WHERE cookie = ? AND isBanned = 0";
        try {
            UUID userId = jdbcTemplate.queryForObject(userSql, UUID.class, cookie);

            if (userId == null)
                throw new RuntimeException("Unauthorized or Banned");

            String checkSql = "SELECT COUNT(*) FROM CommentLikes WHERE CommentId = ? AND UserId = ?";
            Integer count = jdbcTemplate.queryForObject(checkSql, Integer.class, commentId.toString(),
                    userId.toString());

            if (count != null && count > 0) {
                jdbcTemplate.update("DELETE FROM CommentLikes WHERE CommentId = ? AND UserId = ?",
                        commentId.toString(), userId.toString());
                jdbcTemplate.update("UPDATE Comments SET LikeCount = LikeCount - 1 WHERE ID = ?",
                        commentId.toString());
            } else {
                jdbcTemplate.update("INSERT INTO CommentLikes (CommentId, UserId) VALUES (?, ?)",
                        commentId.toString(), userId.toString());
                jdbcTemplate.update("UPDATE Comments SET LikeCount = LikeCount + 1 WHERE ID = ?",
                        commentId.toString());
            }
        } catch (Exception ignored) {
        }
    }

    public String getUserRoleByCookie(String cookie) {
        try {
            return jdbcTemplate.queryForObject("SELECT Role FROM Person WHERE cookie = ?", String.class, cookie);
        } catch (Exception e) {
            return "USER";
        }
    }

    @org.springframework.transaction.annotation.Transactional
    public boolean deleteComment(UUID commentId, String cookie) {
        String role = getUserRoleByCookie(cookie);
        boolean isAdmin = "ADMIN".equalsIgnoreCase(role) || "SUPER_ADMIN".equalsIgnoreCase(role);

        String ownerSql = "SELECT UserId FROM Comments WHERE ID = ?";
        UUID ownerId;
        try {
            ownerId = UUID.fromString(jdbcTemplate.queryForObject(ownerSql, String.class, commentId.toString()));
        } catch (Exception e) {
            return false;
        }

        if (!isAdmin) {
            String requesterIdSql = "SELECT ID FROM Person WHERE cookie = ?";
            UUID requesterId;
            try {
                requesterId = UUID.fromString(jdbcTemplate.queryForObject(requesterIdSql, String.class, cookie));
            } catch (Exception e) {
                return false;
            }

            if (!ownerId.equals(requesterId)) {
                return false;
            }
        }

        String deleteLikesSql = """
            WITH CommentTree AS (
                SELECT ID FROM Comments WHERE ID = ?
                UNION ALL
                SELECT c.ID FROM Comments c
                INNER JOIN CommentTree ct ON c.ParentId = ct.ID
            )
            DELETE FROM CommentLikes WHERE CommentId IN (SELECT ID FROM CommentTree)
        """;

        String deleteCommentsSql = """
            WITH CommentTree AS (
                SELECT ID FROM Comments WHERE ID = ?
                UNION ALL
                SELECT c.ID FROM Comments c
                INNER JOIN CommentTree ct ON c.ParentId = ct.ID
            )
            DELETE FROM Comments WHERE ID IN (SELECT ID FROM CommentTree)
        """;

        jdbcTemplate.update(deleteLikesSql, commentId.toString());
        int affectedRows = jdbcTemplate.update(deleteCommentsSql, commentId.toString());
        return affectedRows > 0;
    }

    private static class CommentRowMapper implements RowMapper<CommentViewModel> {
        @Override
        public CommentViewModel mapRow(@org.springframework.lang.NonNull ResultSet rs, int rowNum) throws SQLException {
            CommentViewModel vm = new CommentViewModel();
            vm.setId(UUID.fromString(rs.getString("ID")));
            vm.setNickname(rs.getString("nickname"));
            vm.setComment(rs.getString("comment"));
            vm.setSpoiler(rs.getBoolean("Spoiler"));
            vm.setLikeCount(rs.getInt("LikeCount"));
            vm.setLikedByCurrentUser(rs.getInt("isLiked") > 0);
            vm.setAuthor(rs.getInt("isAuthor") > 0);

            try {
                String authorIdStr = rs.getString("authorId");
                if (authorIdStr != null) {
                    vm.setAuthorId(UUID.fromString(authorIdStr));
                }
            } catch (SQLException ignored) {
            }

            try {
                vm.setApproved(rs.getBoolean("IsApproved"));
            } catch (SQLException e) {
                vm.setApproved(true);
            }

            try {
                vm.setRole(rs.getString("Role"));
            } catch (SQLException e) {
                vm.setRole("USER");
            }

            try {
                vm.setVerified(rs.getBoolean("isVerified"));
            } catch (SQLException e) {
                vm.setVerified(false);
            }

            String parentIdStr = rs.getString("ParentId");
            if (parentIdStr != null) {
                vm.setParentId(UUID.fromString(parentIdStr));
            }

            String contentIdStr = rs.getString("ContentId");
            if (contentIdStr != null) {
                vm.setContentId(UUID.fromString(contentIdStr));
            }

            java.sql.Timestamp ts = rs.getTimestamp("date");
            if (ts != null) {
                vm.setDate(ts.toInstant());
            }
            return vm;
        }
    }
}
