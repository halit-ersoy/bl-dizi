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
                "IsRead BIT DEFAULT 0)";
        jdbcTemplate.execute(sql);
    }

    public void save(Notification notification) {
        String sql = "INSERT INTO Notifications (ID, Title, Message, ContentID, Type, CreatedAt, IsRead) VALUES (?, ?, ?, ?, ?, ?, ?)";
        jdbcTemplate.update(sql,
                notification.getId().toString(),
                notification.getTitle(),
                notification.getMessage(),
                notification.getContentId().toString(),
                notification.getType(),
                java.sql.Timestamp.from(notification.getCreatedAt()),
                notification.isRead() ? 1 : 0);
    }

    public List<Notification> findRecent(int limit) {
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
                    WHERE (
                        (N.Type = 'Movie' AND EXISTS (SELECT 1 FROM MovieCategories MC WHERE MC.MovieID = N.ContentID AND MC.CategoryID IN (52, 63)))
                        OR
                        (N.Type = 'SoapOpera' AND (
                            EXISTS (SELECT 1 FROM Episode E JOIN SeriesCategories SC ON SC.SeriesID = E.SeriesId WHERE E.ID = N.ContentID AND SC.CategoryID IN (52, 63))
                            OR
                            EXISTS (SELECT 1 FROM SeriesCategories SC WHERE SC.SeriesID = N.ContentID AND SC.CategoryID IN (52, 63))
                        ))
                    )
                    ORDER BY N.CreatedAt DESC
                """;
        return jdbcTemplate.query(sql, new NotificationRowMapper(), limit);
    }

    public void markAsRead(UUID id) {
        String sql = "UPDATE Notifications SET IsRead = 1 WHERE ID = ?";
        jdbcTemplate.update(sql, id.toString());
    }

    public int getUnreadCount() {
        String sql = """
                    SELECT COUNT(*) FROM Notifications N
                    WHERE N.IsRead = 0
                      AND (
                        (N.Type = 'Movie' AND EXISTS (SELECT 1 FROM MovieCategories MC WHERE MC.MovieID = N.ContentID AND MC.CategoryID IN (52, 63)))
                        OR
                        (N.Type = 'SoapOpera' AND (
                            EXISTS (SELECT 1 FROM Episode E JOIN SeriesCategories SC ON SC.SeriesID = E.SeriesId WHERE E.ID = N.ContentID AND SC.CategoryID IN (52, 63))
                            OR
                            EXISTS (SELECT 1 FROM SeriesCategories SC WHERE SC.SeriesID = N.ContentID AND SC.CategoryID IN (52, 63))
                        ))
                      )
                """;
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class);
        return count != null ? count : 0;
    }

    private static class NotificationRowMapper implements RowMapper<Notification> {
        @Override
        public Notification mapRow(@org.springframework.lang.NonNull ResultSet rs, int rowNum) throws SQLException {
            Notification n = new Notification();
            n.setId(UUID.fromString(rs.getString("ID")));
            n.setTitle(rs.getString("Title"));
            n.setMessage(rs.getString("Message"));
            n.setContentId(UUID.fromString(rs.getString("ContentID")));
            n.setType(rs.getString("Type"));
            java.sql.Timestamp ts = rs.getTimestamp("CreatedAt");
            if (ts != null)
                n.setCreatedAt(ts.toInstant());
            n.setRead(rs.getBoolean("IsRead"));

            try {
                n.setSlug(rs.getString("slug"));
            } catch (SQLException e) {
                // Ignore if not present
            }

            return n;
        }
    }
}
