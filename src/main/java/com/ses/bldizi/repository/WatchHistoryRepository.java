package com.ses.bldizi.repository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Repository
@SuppressWarnings({"SqlResolve", "SqlNoDataSourceInspection"})
public class WatchHistoryRepository {

    private static final Logger logger = LoggerFactory.getLogger(WatchHistoryRepository.class);
    private final JdbcTemplate jdbcTemplate;

    public WatchHistoryRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        ensureSchema();
    }

    private void ensureSchema() {
        try {
            jdbcTemplate.execute("""
                    IF NOT EXISTS (SELECT * FROM sysobjects WHERE name='BLDizi_UserWatchHistory' AND xtype='U')
                    BEGIN
                        CREATE TABLE BLDizi_UserWatchHistory (
                            ID UNIQUEIDENTIFIER DEFAULT NEWID() PRIMARY KEY,
                            UserID UNIQUEIDENTIFIER NOT NULL,
                            ContentId UNIQUEIDENTIFIER NOT NULL,
                            ContentType NVARCHAR(20) NOT NULL,
                            WatchedAt DATETIME DEFAULT GETDATE() NOT NULL,
                            CONSTRAINT FK_BLDizi_UserWatchHistory_Person FOREIGN KEY (UserID) REFERENCES Person(ID) ON DELETE CASCADE
                        );
                    END
                    """);

            jdbcTemplate.execute("""
                    IF NOT EXISTS (SELECT * FROM sys.indexes WHERE name = 'idx_bldizi_userwatchhistory_user_watched' AND object_id = OBJECT_ID('BLDizi_UserWatchHistory'))
                    BEGIN
                        CREATE NONCLUSTERED INDEX idx_bldizi_userwatchhistory_user_watched ON BLDizi_UserWatchHistory(UserID, WatchedAt DESC);
                    END
                    """);

            jdbcTemplate.execute("""
                    IF NOT EXISTS (SELECT * FROM sys.indexes WHERE name = 'idx_bldizi_userwatchhistory_user_content' AND object_id = OBJECT_ID('BLDizi_UserWatchHistory'))
                    BEGIN
                        CREATE UNIQUE NONCLUSTERED INDEX idx_bldizi_userwatchhistory_user_content ON BLDizi_UserWatchHistory(UserID, ContentId);
                    END
                    """);
            logger.info("BLDizi_UserWatchHistory schema ensured successfully.");
        } catch (Exception e) {
            logger.error("Error creating BLDizi_UserWatchHistory schema: {}", e.getMessage(), e);
        }
    }

    public void recordWatch(UUID userId, UUID contentId) {
        if (userId == null || contentId == null) {
            return;
        }

        try {
            // Determine content type
            String contentType = determineContentType(contentId);
            if (contentType == null) {
                contentType = "movie";
            }

            String sql = """
                    UPDATE BLDizi_UserWatchHistory
                    SET WatchedAt = GETDATE(), ContentType = ?
                    WHERE UserID = ? AND ContentId = ?;

                    IF @@ROWCOUNT = 0
                    BEGIN
                        INSERT INTO BLDizi_UserWatchHistory (UserID, ContentId, ContentType, WatchedAt)
                        VALUES (?, ?, ?, GETDATE());
                    END
                    """;

            jdbcTemplate.update(sql, contentType, userId, contentId, userId, contentId, contentType);
        } catch (Exception e) {
            logger.error("Error recording watch history for user {} and content {}: {}", userId, contentId, e.getMessage());
        }
    }

    private String determineContentType(UUID contentId) {
        try {
            Integer movieCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM Movie WHERE ID = ?", Integer.class, contentId.toString());
            if (movieCount != null && movieCount > 0) {
                return "movie";
            }

            Integer episodeCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM Episode WHERE ID = ?", Integer.class, contentId.toString());
            if (episodeCount != null && episodeCount > 0) {
                return "episode";
            }

            Integer seriesCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM Series WHERE ID = ?", Integer.class, contentId.toString());
            if (seriesCount != null && seriesCount > 0) {
                return "series";
            }
        } catch (Exception ignored) {
        }
        return "movie";
    }

    public List<Map<String, Object>> getUserWatchHistory(UUID userId) {
        String sql = """
                SELECT
                    H.ID AS historyId,
                    H.ContentId AS contentId,
                    H.ContentType AS contentType,
                    H.WatchedAt AS watchedAt,
                    M.name AS movieName,
                    M.slug AS movieSlug,
                    M.ReleaseYear AS movieReleaseYear,
                    M.DurationMinutes AS movieDuration,
                    E.name AS episodeName,
                    E.slug AS episodeSlug,
                    E.SeasonNumber AS seasonNumber,
                    E.EpisodeNumber AS episodeNumber,
                    E.DurationMinutes AS episodeDuration,
                    S.name AS seriesName,
                    S.slug AS seriesSlug,
                    S.ID AS seriesId,
                    CASE
                        WHEN H.ContentType = 'movie' THEN M.name
                        WHEN H.ContentType = 'episode' THEN
                            CASE
                                WHEN E.name IS NOT NULL AND LEN(LTRIM(RTRIM(E.name))) > 0
                                THEN CONCAT(S.name, ' - S', E.SeasonNumber, ' B', E.EpisodeNumber, ': ', E.name)
                                ELSE CONCAT(S.name, ' - Sezon ', E.SeasonNumber, ' Bölüm ', E.EpisodeNumber)
                            END
                        WHEN H.ContentType = 'series' THEN S.name
                        ELSE COALESCE(M.name, S.name, E.name, 'İçerik')
                    END AS displayName,
                    CASE
                        WHEN H.ContentType = 'movie' THEN M.slug
                        WHEN H.ContentType = 'episode' THEN COALESCE(E.slug, S.slug)
                        WHEN H.ContentType = 'series' THEN S.slug
                        ELSE COALESCE(M.slug, S.slug, E.slug)
                    END AS targetSlug,
                    CASE
                        WHEN H.ContentType = 'movie' THEN M.ID
                        WHEN H.ContentType = 'episode' THEN COALESCE(S.ID, E.ID)
                        WHEN H.ContentType = 'series' THEN S.ID
                        ELSE H.ContentId
                    END AS imageId
                FROM BLDizi_UserWatchHistory H
                LEFT JOIN Movie M ON H.ContentId = M.ID AND H.ContentType = 'movie'
                LEFT JOIN Episode E ON H.ContentId = E.ID AND H.ContentType = 'episode'
                LEFT JOIN Series S ON (H.ContentType = 'episode' AND E.SeriesId = S.ID) OR (H.ContentType = 'series' AND H.ContentId = S.ID)
                WHERE H.UserID = ?
                ORDER BY H.WatchedAt DESC
                """;

        return jdbcTemplate.queryForList(sql, userId);
    }

    public boolean deleteHistoryItem(UUID userId, UUID historyId) {
        try {
            int rows = jdbcTemplate.update(
                    "DELETE FROM BLDizi_UserWatchHistory WHERE ID = ? AND UserID = ?", historyId, userId);
            return rows > 0;
        } catch (Exception e) {
            logger.error("Error deleting history item {} for user {}: {}", historyId, userId, e.getMessage());
            return false;
        }
    }

    public boolean clearUserHistory(UUID userId) {
        try {
            jdbcTemplate.update("DELETE FROM BLDizi_UserWatchHistory WHERE UserID = ?", userId);
            return true;
        } catch (Exception e) {
            logger.error("Error clearing watch history for user {}: {}", userId, e.getMessage());
            return false;
        }
    }
}
