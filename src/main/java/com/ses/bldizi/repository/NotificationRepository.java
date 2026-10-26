package com.ses.bldizi.repository;

import com.ses.bldizi.model.Notification;
import jakarta.annotation.PostConstruct;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

@Repository
@SuppressWarnings({"SqlResolve", "SqlNoDataSourceInspection"})
public class NotificationRepository {
    private final JdbcTemplate jdbcTemplate;

    public NotificationRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    public void init() {
        String sql = "IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = 'Notifications') " +
                "CREATE TABLE Notifications (" +
                "ID UNIQUEIDENTIFIER PRIMARY KEY, " +
                "Title NVARCHAR(255), " +
                "Message NVARCHAR(MAX), " +
                "ContentID UNIQUEIDENTIFIER, " +
                "Type NVARCHAR(50), " +
                "CreatedAt DATETIME, " +
                "IsRead BIT DEFAULT 0, " +
                "UserId UNIQUEIDENTIFIER NULL)";
        jdbcTemplate.execute(sql);

        try {
            jdbcTemplate.execute("IF NOT EXISTS (SELECT * FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'Notifications' AND COLUMN_NAME = 'UserId') " +
                    "ALTER TABLE Notifications ADD UserId UNIQUEIDENTIFIER NULL");
        } catch (Exception ignored) {}
    }

    public void save(Notification notification) {
        String sql = "INSERT INTO Notifications (ID, Title, Message, ContentID, Type, CreatedAt, IsRead, UserId) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        jdbcTemplate.update(sql,
                notification.getId().toString(),
                notification.getTitle(),
                notification.getMessage(),
                notification.getContentId() != null ? notification.getContentId().toString() : null,
                notification.getType(),
                java.sql.Timestamp.from(notification.getCreatedAt()),
                notification.isRead() ? 1 : 0,
                notification.getUserId() != null ? notification.getUserId().toString() : null);
    }

    public List<Notification> findRecent(int limit) {
        return findRecent(limit, null);
    }

    public List<Notification> findRecent(int limit, UUID userId) {
        if (userId != null) {
            String sql = """
                    SELECT TOP (?) N.*,
                           CASE
                               WHEN N.Type = 'Movie' THEN (SELECT M.slug FROM Movie M WHERE M.ID = N.ContentID)
                               WHEN N.Type = 'SoapOpera' THEN COALESCE(
                                   (SELECT E.slug FROM Episode E WHERE E.ID = N.ContentID),
                                   (SELECT S.slug FROM Series S WHERE S.ID = N.ContentID)
                               )
                               ELSE COALESCE(
                                   (SELECT M.slug FROM Movie M WHERE M.ID = N.ContentID),
                                   (SELECT E.slug FROM Episode E WHERE E.ID = N.ContentID),
                                   (SELECT S.slug FROM Series S WHERE S.ID = N.ContentID),
                                   (SELECT U.slug FROM Upcoming U WHERE U.ID = N.ContentID)
                               )
                           END as slug
                    FROM Notifications N
                    WHERE (N.UserId = ? OR N.UserId IS NULL)
                      AND (
                        (N.Type = 'Movie' AND (EXISTS (SELECT 1 FROM MovieCategories MC WHERE MC.MovieID = N.ContentID AND MC.CategoryID IN (52, 63)) OR EXISTS (SELECT 1 FROM Movie M WHERE M.ID = N.ContentID AND (M.MovieType IN ('BL', 'GL') OR M.isBl = 1))))
                        OR
                        (N.Type = 'SoapOpera' AND (
                            EXISTS (SELECT 1 FROM Episode E JOIN Series S ON S.ID = E.SeriesId WHERE E.ID = N.ContentID AND (S.SeriesType IN ('BL', 'GL') OR S.isBl = 1 OR EXISTS (SELECT 1 FROM SeriesCategories SC WHERE SC.SeriesID = S.ID AND SC.CategoryID IN (52, 63))))
                            OR
                            EXISTS (SELECT 1 FROM Series S WHERE S.ID = N.ContentID AND (S.SeriesType IN ('BL', 'GL') OR S.isBl = 1 OR EXISTS (SELECT 1 FROM SeriesCategories SC WHERE SC.SeriesID = S.ID AND SC.CategoryID IN (52, 63))))
                        ))
                        OR
                        (N.Type = 'CommentReply' AND (
                            EXISTS (SELECT 1 FROM MovieCategories MC WHERE MC.MovieID = N.ContentID AND MC.CategoryID IN (52, 63))
                            OR EXISTS (SELECT 1 FROM Movie M WHERE M.ID = N.ContentID AND (M.MovieType IN ('BL', 'GL') OR M.isBl = 1))
                            OR EXISTS (SELECT 1 FROM Episode E JOIN Series S ON S.ID = E.SeriesId WHERE E.ID = N.ContentID AND (S.SeriesType IN ('BL', 'GL') OR S.isBl = 1 OR EXISTS (SELECT 1 FROM SeriesCategories SC WHERE SC.SeriesID = S.ID AND SC.CategoryID IN (52, 63))))
                            OR EXISTS (SELECT 1 FROM Series S WHERE S.ID = N.ContentID AND (S.SeriesType IN ('BL', 'GL') OR S.isBl = 1 OR EXISTS (SELECT 1 FROM SeriesCategories SC WHERE SC.SeriesID = S.ID AND SC.CategoryID IN (52, 63))))
                        ))
                      )
                    ORDER BY N.CreatedAt DESC
                """;
            return jdbcTemplate.query(sql, new NotificationRowMapper(), limit, userId.toString());
        } else {
            String sql = """
                    SELECT TOP (?) N.*,
                           CASE
                               WHEN N.Type = 'Movie' THEN (SELECT M.slug FROM Movie M WHERE M.ID = N.ContentID)
                               ELSE COALESCE(
                                   (SELECT E.slug FROM Episode E WHERE E.ID = N.ContentID),
                                   (SELECT S.slug FROM Series S WHERE S.ID = N.ContentID)
                               )
                           END as slug
                    FROM Notifications N
                    WHERE N.UserId IS NULL AND (
                        (N.Type = 'Movie' AND (EXISTS (SELECT 1 FROM MovieCategories MC WHERE MC.MovieID = N.ContentID AND MC.CategoryID IN (52, 63)) OR EXISTS (SELECT 1 FROM Movie M WHERE M.ID = N.ContentID AND (M.MovieType IN ('BL', 'GL') OR M.isBl = 1))))
                        OR
                        (N.Type = 'SoapOpera' AND (
                            EXISTS (SELECT 1 FROM Episode E JOIN Series S ON S.ID = E.SeriesId WHERE E.ID = N.ContentID AND (S.SeriesType IN ('BL', 'GL') OR S.isBl = 1 OR EXISTS (SELECT 1 FROM SeriesCategories SC WHERE SC.SeriesID = S.ID AND SC.CategoryID IN (52, 63))))
                            OR
                            EXISTS (SELECT 1 FROM Series S WHERE S.ID = N.ContentID AND (S.SeriesType IN ('BL', 'GL') OR S.isBl = 1 OR EXISTS (SELECT 1 FROM SeriesCategories SC WHERE SC.SeriesID = S.ID AND SC.CategoryID IN (52, 63))))
                        ))
                    )
                    ORDER BY N.CreatedAt DESC
                """;
            return jdbcTemplate.query(sql, new NotificationRowMapper(), limit);
        }
    }

    public void markAsRead(UUID id) {
        String sql = "UPDATE Notifications SET IsRead = 1 WHERE ID = ?";
        jdbcTemplate.update(sql, id.toString());
    }

    public int getUnreadCount() {
        return getUnreadCount(null);
    }

    public int getUnreadCount(UUID userId) {
        if (userId != null) {
            String sql = """
                    SELECT COUNT(*) FROM Notifications N
                    WHERE N.IsRead = 0
                      AND (N.UserId = ? OR N.UserId IS NULL)
                      AND (
                        (N.Type = 'Movie' AND (EXISTS (SELECT 1 FROM MovieCategories MC WHERE MC.MovieID = N.ContentID AND MC.CategoryID IN (52, 63)) OR EXISTS (SELECT 1 FROM Movie M WHERE M.ID = N.ContentID AND (M.MovieType IN ('BL', 'GL') OR M.isBl = 1))))
                        OR
                        (N.Type = 'SoapOpera' AND (
                            EXISTS (SELECT 1 FROM Episode E JOIN Series S ON S.ID = E.SeriesId WHERE E.ID = N.ContentID AND (S.SeriesType IN ('BL', 'GL') OR S.isBl = 1 OR EXISTS (SELECT 1 FROM SeriesCategories SC WHERE SC.SeriesID = S.ID AND SC.CategoryID IN (52, 63))))
                            OR
                            EXISTS (SELECT 1 FROM Series S WHERE S.ID = N.ContentID AND (S.SeriesType IN ('BL', 'GL') OR S.isBl = 1 OR EXISTS (SELECT 1 FROM SeriesCategories SC WHERE SC.SeriesID = S.ID AND SC.CategoryID IN (52, 63))))
                        ))
                        OR
                        (N.Type = 'CommentReply' AND (
                            EXISTS (SELECT 1 FROM MovieCategories MC WHERE MC.MovieID = N.ContentID AND MC.CategoryID IN (52, 63))
                            OR EXISTS (SELECT 1 FROM Movie M WHERE M.ID = N.ContentID AND (M.MovieType IN ('BL', 'GL') OR M.isBl = 1))
                            OR EXISTS (SELECT 1 FROM Episode E JOIN Series S ON S.ID = E.SeriesId WHERE E.ID = N.ContentID AND (S.SeriesType IN ('BL', 'GL') OR S.isBl = 1 OR EXISTS (SELECT 1 FROM SeriesCategories SC WHERE SC.SeriesID = S.ID AND SC.CategoryID IN (52, 63))))
                            OR EXISTS (SELECT 1 FROM Series S WHERE S.ID = N.ContentID AND (S.SeriesType IN ('BL', 'GL') OR S.isBl = 1 OR EXISTS (SELECT 1 FROM SeriesCategories SC WHERE SC.SeriesID = S.ID AND SC.CategoryID IN (52, 63))))
                        ))
                      )
                """;
            Integer count = jdbcTemplate.queryForObject(sql, Integer.class, userId.toString());
            return count != null ? count : 0;
        } else {
            String sql = """
                    SELECT COUNT(*) FROM Notifications N
                    WHERE N.IsRead = 0
                      AND N.UserId IS NULL
                      AND (
                        (N.Type = 'Movie' AND (EXISTS (SELECT 1 FROM MovieCategories MC WHERE MC.MovieID = N.ContentID AND MC.CategoryID IN (52, 63)) OR EXISTS (SELECT 1 FROM Movie M WHERE M.ID = N.ContentID AND (M.MovieType IN ('BL', 'GL') OR M.isBl = 1))))
                        OR
                        (N.Type = 'SoapOpera' AND (
                            EXISTS (SELECT 1 FROM Episode E JOIN Series S ON S.ID = E.SeriesId WHERE E.ID = N.ContentID AND (S.SeriesType IN ('BL', 'GL') OR S.isBl = 1 OR EXISTS (SELECT 1 FROM SeriesCategories SC WHERE SC.SeriesID = S.ID AND SC.CategoryID IN (52, 63))))
                            OR
                            EXISTS (SELECT 1 FROM Series S WHERE S.ID = N.ContentID AND (S.SeriesType IN ('BL', 'GL') OR S.isBl = 1 OR EXISTS (SELECT 1 FROM SeriesCategories SC WHERE SC.SeriesID = S.ID AND SC.CategoryID IN (52, 63))))
                        ))
                      )
                """;
            Integer count = jdbcTemplate.queryForObject(sql, Integer.class);
            return count != null ? count : 0;
        }
    }

    public UUID resolveUserIdByCookie(String cookie) {
        if (cookie == null || cookie.trim().isEmpty()) return null;
        try {
            String sql = "SELECT [dbo].[ResolveUserIdByCookie](TRY_CAST(? AS UNIQUEIDENTIFIER))";
            return jdbcTemplate.queryForObject(sql, UUID.class, cookie.trim());
        } catch (Exception e) {
            return null;
        }
    }

    private static class NotificationRowMapper implements RowMapper<Notification> {
        @Override
        public Notification mapRow(@org.springframework.lang.NonNull ResultSet rs, int rowNum) throws SQLException {
            Notification n = new Notification();
            n.setId(UUID.fromString(rs.getString("ID")));
            n.setTitle(rs.getString("Title"));
            n.setMessage(rs.getString("Message"));
            String cid = rs.getString("ContentID");
            if (cid != null) {
                try {
                    n.setContentId(UUID.fromString(cid));
                } catch (Exception ignored) {}
            }
            n.setType(rs.getString("Type"));
            java.sql.Timestamp ts = rs.getTimestamp("CreatedAt");
            if (ts != null)
                n.setCreatedAt(ts.toInstant());
            n.setRead(rs.getBoolean("IsRead"));

            try {
                String uid = rs.getString("UserId");
                if (uid != null) n.setUserId(UUID.fromString(uid));
            } catch (SQLException ignored) {}

            try {
                n.setSlug(rs.getString("slug"));
            } catch (SQLException e) {
                // Ignore if not present
            }

            return n;
        }
    }
}
