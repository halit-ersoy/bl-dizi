package com.ses.bldizi.repository;

import com.ses.bldizi.model.StreamLive;
import com.ses.bldizi.model.StreamLiveChat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;

@Repository
@SuppressWarnings({"SqlResolve", "SqlNoDataSourceInspection", "SqlRedundantOrderingDirection"})
public class StreamLiveRepository {

    private static final Logger logger = LoggerFactory.getLogger(StreamLiveRepository.class);
    private final JdbcTemplate jdbcTemplate;

    public StreamLiveRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        ensureSchema();
    }

    private void ensureSchema() {
        try {
            jdbcTemplate.execute("""
                IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = 'StreamLive')
                BEGIN
                    CREATE TABLE StreamLive (\n                        ID UNIQUEIDENTIFIER PRIMARY KEY DEFAULT NEWID(),\n                        Title NVARCHAR(255) NOT NULL,\n                        Description NVARCHAR(MAX) NULL,\n                        ContentType NVARCHAR(50) NOT NULL,\n                        ContentId UNIQUEIDENTIFIER NOT NULL,\n                        ContentName NVARCHAR(255) NULL,\n                        Slug NVARCHAR(255) NULL,\n                        PosterUrl NVARCHAR(500) NULL,\n                        VideoUrl NVARCHAR(500) NOT NULL,\n                        ScheduledStartTime DATETIME NOT NULL,\n                        DurationMinutes INT NOT NULL DEFAULT 0,\n                        DurationSeconds INT NOT NULL DEFAULT 0,\n                        EndTime DATETIME NOT NULL,\n                        IsActive BIT NOT NULL DEFAULT 1,\n                        CreatedAt DATETIME NOT NULL DEFAULT GETDATE()\n                    );\n                    CREATE NONCLUSTERED INDEX idx_streamlive_schedule ON StreamLive(ScheduledStartTime, EndTime, IsActive);\n                END
            """);

            jdbcTemplate.execute("""
                IF NOT EXISTS (
                    SELECT * FROM sys.columns 
                    WHERE object_id = OBJECT_ID('StreamLive') AND name = 'DurationSeconds'
                )
                BEGIN
                    ALTER TABLE StreamLive ADD DurationSeconds INT NOT NULL DEFAULT 0;
                END
            """);

            jdbcTemplate.execute("""
                IF NOT EXISTS (
                    SELECT * FROM sys.columns 
                    WHERE object_id = OBJECT_ID('StreamLive') AND name = 'Platform'
                )
                BEGIN
                    ALTER TABLE StreamLive ADD Platform NVARCHAR(20) NOT NULL DEFAULT 'BLDIZI';
                END
            """);

            jdbcTemplate.execute("""
                IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = 'StreamLiveChat')
                BEGIN
                    CREATE TABLE StreamLiveChat (
                        ID UNIQUEIDENTIFIER PRIMARY KEY DEFAULT NEWID(),
                        StreamLiveId UNIQUEIDENTIFIER NOT NULL,
                        UserId UNIQUEIDENTIFIER NOT NULL,
                        Nickname NVARCHAR(100) NOT NULL,
                        Message NVARCHAR(200) NOT NULL,
                        SentAt DATETIME NOT NULL DEFAULT GETDATE()
                    );
                    CREATE NONCLUSTERED INDEX idx_streamlivechat_streamid ON StreamLiveChat(StreamLiveId, SentAt ASC);
                END
            """);
        } catch (Exception e) {
            logger.error("Failed to ensure StreamLive schema: {}", e.getMessage());
        }
    }

    private final RowMapper<StreamLive> streamLiveRowMapper = (rs, rowNum) -> {
        StreamLive s = new StreamLive();
        s.setId(UUID.fromString(rs.getString("ID")));
        s.setTitle(rs.getString("Title"));
        s.setDescription(rs.getString("Description"));
        s.setContentType(rs.getString("ContentType"));
        s.setContentId(UUID.fromString(rs.getString("ContentId")));
        s.setContentName(rs.getString("ContentName"));
        s.setSlug(rs.getString("Slug"));
        s.setPosterUrl(rs.getString("PosterUrl"));
        s.setVideoUrl(rs.getString("VideoUrl"));
        s.setScheduledStartTime(rs.getTimestamp("ScheduledStartTime"));
        s.setDurationMinutes(rs.getInt("DurationMinutes"));
        try {
            int durSec = rs.getInt("DurationSeconds");
            s.setDurationSeconds(durSec > 0 ? durSec : s.getDurationMinutes() * 60);
        } catch (Exception ignored) {
            s.setDurationSeconds(s.getDurationMinutes() * 60);
        }
        s.setEndTime(rs.getTimestamp("EndTime"));
        s.setActive(rs.getBoolean("IsActive"));
        s.setCreatedAt(rs.getTimestamp("CreatedAt"));
        try {
            String plat = rs.getString("Platform");
            s.setPlatform(plat != null && !plat.isBlank() ? plat : "BLDIZI");
        } catch (Exception ignored) {
            s.setPlatform("BLDIZI");
        }
        return s;
    };

    private final RowMapper<StreamLiveChat> streamLiveChatRowMapper = (rs, rowNum) -> {
        StreamLiveChat c = new StreamLiveChat();
        c.setId(UUID.fromString(rs.getString("ID")));
        c.setStreamLiveId(UUID.fromString(rs.getString("StreamLiveId")));
        c.setUserId(UUID.fromString(rs.getString("UserId")));
        c.setNickname(rs.getString("Nickname"));
        c.setMessage(rs.getString("Message"));
        c.setSentAt(rs.getTimestamp("SentAt"));
        try {
            c.setVerified(rs.getBoolean("isVerified"));
        } catch (Exception ignored) {
        }
        return c;
    };

    public void createStreamLive(StreamLive s) {
        String sql = """
            INSERT INTO StreamLive (ID, Title, Description, ContentType, ContentId, ContentName, Slug, PosterUrl, VideoUrl, ScheduledStartTime, DurationMinutes, DurationSeconds, EndTime, IsActive, CreatedAt, Platform)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;
        jdbcTemplate.update(sql,
                s.getId().toString(),
                s.getTitle(),
                s.getDescription(),
                s.getContentType(),
                s.getContentId().toString(),
                s.getContentName(),
                s.getSlug(),
                s.getPosterUrl(),
                s.getVideoUrl(),
                s.getScheduledStartTime(),
                s.getDurationMinutes(),
                s.getDurationSeconds(),
                s.getEndTime(),
                s.isActive() ? 1 : 0,
                s.getCreatedAt(),
                s.getPlatform());
    }

    public void updateStreamLive(StreamLive s) {
        String sql = """
            UPDATE StreamLive
            SET Title = ?, Description = ?, ContentType = ?, ContentId = ?, ContentName = ?, Slug = ?, PosterUrl = ?, VideoUrl = ?, ScheduledStartTime = ?, DurationMinutes = ?, DurationSeconds = ?, EndTime = ?, IsActive = ?, Platform = ?
            WHERE ID = ?
        """;
        jdbcTemplate.update(sql,
                s.getTitle(),
                s.getDescription(),
                s.getContentType(),
                s.getContentId().toString(),
                s.getContentName(),
                s.getSlug(),
                s.getPosterUrl(),
                s.getVideoUrl(),
                s.getScheduledStartTime(),
                s.getDurationMinutes(),
                s.getDurationSeconds(),
                s.getEndTime(),
                s.isActive() ? 1 : 0,
                s.getPlatform(),
                s.getId().toString());
    }

    public void deleteStreamLive(UUID id) {
        jdbcTemplate.update("DELETE FROM StreamLiveChat WHERE StreamLiveId = ?", id.toString());
        jdbcTemplate.update("DELETE FROM StreamLive WHERE ID = ?", id.toString());
    }

    public StreamLive findStreamById(UUID id) {
        try {
            return jdbcTemplate.queryForObject("SELECT * FROM StreamLive WHERE ID = ?", streamLiveRowMapper, id.toString());
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Finds the primary active or next scheduled stream:
     * 1. Currently live stream (ScheduledStartTime <= NOW <= EndTime)
     * 2. If none live, nearest upcoming stream (ScheduledStartTime > NOW)
     * 3. If none upcoming, recently ended stream within 1 hour (EndTime <= NOW <= EndTime + 1 hour)
     */
    public StreamLive findActiveOrUpcomingStream() {
        return findActiveOrUpcomingStream("BLDIZI");
    }

    public StreamLive findActiveOrUpcomingStream(String platform) {
        Timestamp now = new Timestamp(System.currentTimeMillis());
        boolean isAll = "ALL".equalsIgnoreCase(platform);
        String platFilter = isAll ? "" : " AND (Platform = ? OR Platform = 'ALL' OR Platform IS NULL)";

        // 1. Check if there's an ongoing live stream
        try {
            String sqlLive = "SELECT TOP 1 * FROM StreamLive WHERE IsActive = 1 AND ScheduledStartTime <= ? AND EndTime >= ?" + platFilter + " ORDER BY ScheduledStartTime ASC";
            List<StreamLive> live = isAll
                    ? jdbcTemplate.query(sqlLive, streamLiveRowMapper, now, now)
                    : jdbcTemplate.query(sqlLive, streamLiveRowMapper, now, now, platform);
            if (!live.isEmpty()) return live.get(0);
        } catch (Exception ignored) {}

        // 2. Check for next upcoming stream
        try {
            String sqlUpcoming = "SELECT TOP 1 * FROM StreamLive WHERE IsActive = 1 AND ScheduledStartTime > ?" + platFilter + " ORDER BY ScheduledStartTime ASC";
            List<StreamLive> upcoming = isAll
                    ? jdbcTemplate.query(sqlUpcoming, streamLiveRowMapper, now)
                    : jdbcTemplate.query(sqlUpcoming, streamLiveRowMapper, now, platform);
            if (!upcoming.isEmpty()) return upcoming.get(0);
        } catch (Exception ignored) {}

        // 3. Check for recently ended stream (within last 60 minutes)
        try {
            Timestamp sixtyMinutesAgo = new Timestamp(System.currentTimeMillis() - 60L * 60L * 1000L);
            String sqlRecentEnded = "SELECT TOP 1 * FROM StreamLive WHERE IsActive = 1 AND EndTime < ? AND EndTime >= ?" + platFilter + " ORDER BY EndTime DESC";
            List<StreamLive> ended = isAll
                    ? jdbcTemplate.query(sqlRecentEnded, streamLiveRowMapper, now, sixtyMinutesAgo)
                    : jdbcTemplate.query(sqlRecentEnded, streamLiveRowMapper, now, sixtyMinutesAgo, platform);
            if (!ended.isEmpty()) return ended.get(0);
        } catch (Exception ignored) {}

        return null;
    }

    /**
     * Finds the most relevant stream for the schedule balloon on the homepage:
     * 1. Ongoing live stream (ScheduledStartTime <= NOW <= EndTime)
     * 2. Next upcoming scheduled stream (ScheduledStartTime > NOW, ordered by ScheduledStartTime ASC)
     * If neither exists, returns null (so nothing is displayed on the homepage).
     */
    public StreamLive findScheduleOrLatestStream() {
        return findScheduleOrLatestStream("BLDIZI");
    }

    public StreamLive findScheduleOrLatestStream(String platform) {
        Timestamp now = new Timestamp(System.currentTimeMillis());
        boolean isAll = "ALL".equalsIgnoreCase(platform);
        String platFilter = isAll ? "" : " AND (Platform = ? OR Platform = 'ALL' OR Platform IS NULL)";

        // 1. Ongoing live stream
        try {
            String sqlLive = "SELECT TOP 1 * FROM StreamLive WHERE IsActive = 1 AND ScheduledStartTime <= ? AND EndTime >= ?" + platFilter + " ORDER BY ScheduledStartTime ASC";
            List<StreamLive> live = isAll
                    ? jdbcTemplate.query(sqlLive, streamLiveRowMapper, now, now)
                    : jdbcTemplate.query(sqlLive, streamLiveRowMapper, now, now, platform);
            if (!live.isEmpty()) return live.get(0);
        } catch (Exception ignored) {}

        // 2. Next upcoming stream
        try {
            String sqlUpcoming = "SELECT TOP 1 * FROM StreamLive WHERE IsActive = 1 AND ScheduledStartTime > ?" + platFilter + " ORDER BY ScheduledStartTime ASC";
            List<StreamLive> upcoming = isAll
                    ? jdbcTemplate.query(sqlUpcoming, streamLiveRowMapper, now)
                    : jdbcTemplate.query(sqlUpcoming, streamLiveRowMapper, now, platform);
            if (!upcoming.isEmpty()) return upcoming.get(0);
        } catch (Exception ignored) {}

        return null;
    }

    public List<StreamLive> findAllStreamsForAdmin() {
        return jdbcTemplate.query("SELECT * FROM StreamLive ORDER BY ScheduledStartTime DESC", streamLiveRowMapper);
    }

    public void saveChatMessage(StreamLiveChat chat) {
        String sql = """
            INSERT INTO StreamLiveChat (ID, StreamLiveId, UserId, Nickname, Message, SentAt)
            VALUES (?, ?, ?, ?, ?, ?)
        """;
        jdbcTemplate.update(sql,
                chat.getId().toString(),
                chat.getStreamLiveId().toString(),
                chat.getUserId().toString(),
                chat.getNickname(),
                chat.getMessage(),
                chat.getSentAt());
    }

    public List<StreamLiveChat> getRecentChatMessages(UUID streamLiveId, int limit) {
        String sql = """
            SELECT Sub.ID, Sub.StreamLiveId, Sub.UserId, Sub.Nickname, Sub.Message, Sub.SentAt, ISNULL(p.isVerified, 0) AS isVerified
            FROM (
                SELECT TOP (?) * FROM StreamLiveChat
                WHERE StreamLiveId = ?
                ORDER BY SentAt DESC
            ) Sub
            LEFT JOIN [Person] p ON Sub.UserId = p.ID
            ORDER BY SentAt ASC
        """;
        return jdbcTemplate.query(sql, streamLiveChatRowMapper, limit, streamLiveId.toString());
    }

    /**
     * Delete chat messages for streams that ended before cutoffTime.
     */
    public int deleteChatMessagesForEndedStreams(Timestamp cutoffTime) {
        String sql = """
            DELETE FROM StreamLiveChat
            WHERE StreamLiveId IN (
                SELECT ID FROM StreamLive WHERE EndTime < ?
            )
        """;
        return jdbcTemplate.update(sql, cutoffTime);
    }

    public boolean isUserVerified(UUID userId) {
        if (userId == null) return false;
        try {
            Boolean verified = jdbcTemplate.queryForObject(
                    "SELECT TOP 1 isVerified FROM [Person] WHERE ID = ?",
                    Boolean.class,
                    userId.toString()
            );
            return verified != null && verified;
        } catch (Exception e) {
            return false;
        }
    }
}
