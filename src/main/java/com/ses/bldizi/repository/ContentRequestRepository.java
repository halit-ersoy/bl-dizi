package com.ses.bldizi.repository;

import com.ses.bldizi.model.ContentRequest;
import com.ses.bldizi.service.TmdbService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

@Repository
@SuppressWarnings({"SqlResolve", "SqlNoDataSourceInspection"})
public class ContentRequestRepository {

    private final JdbcTemplate jdbcTemplate;

    @Autowired
    public ContentRequestRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        ensureTablesExist();
    }

    public void ensureTablesExist() {
        String sqlRequests = """
                IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = 'ContentRequest')
                BEGIN
                    CREATE TABLE ContentRequest (
                        Id UNIQUEIDENTIFIER PRIMARY KEY DEFAULT NEWID(),
                        UserId UNIQUEIDENTIFIER NOT NULL,
                        UserNickname NVARCHAR(100) NOT NULL,
                        Title NVARCHAR(255) NOT NULL,
                        ContentType NVARCHAR(20) NOT NULL,
                        PosterUrl NVARCHAR(500) NULL,
                        ReleaseYear INT NULL,
                        TmdbId INT NULL,
                        TvMazeId INT NULL,
                        Description NVARCHAR(1000) NULL,
                        Status NVARCHAR(50) DEFAULT 'PENDING',
                        VoteCount INT DEFAULT 1,
                        CreatedAt DATETIME2 DEFAULT GETUTCDATE(),
                        CompletedAt DATETIME2 NULL,
                        IsBl BIT DEFAULT 0 NOT NULL,
                        Platform NVARCHAR(50) DEFAULT 'whodatidols' NOT NULL
                    );
                END
                """;

        String sqlVotes = """
                IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = 'ContentRequestVote')
                BEGIN
                    CREATE TABLE ContentRequestVote (
                        RequestId UNIQUEIDENTIFIER NOT NULL,
                        UserId UNIQUEIDENTIFIER NOT NULL,
                        CreatedAt DATETIME2 DEFAULT GETUTCDATE(),
                        PRIMARY KEY (RequestId, UserId)
                    );
                END
                """;

        try {
            jdbcTemplate.execute(sqlRequests);
            jdbcTemplate.execute(sqlVotes);

            // Kolonlar yoksa ekle (IsBl ve Platform)
            jdbcTemplate.execute("""
                    IF NOT EXISTS (SELECT * FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'ContentRequest' AND COLUMN_NAME = 'IsBl')
                    BEGIN
                        ALTER TABLE ContentRequest ADD IsBl BIT DEFAULT 0 NOT NULL;
                    END
                    """);

            jdbcTemplate.execute("""
                    IF NOT EXISTS (SELECT * FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'ContentRequest' AND COLUMN_NAME = 'Platform')
                    BEGIN
                        ALTER TABLE ContentRequest ADD Platform NVARCHAR(50) DEFAULT 'whodatidols' NOT NULL;
                    END
                    """);

            jdbcTemplate.execute("""
                    IF NOT EXISTS (SELECT * FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'ContentRequest' AND COLUMN_NAME = 'CompletedAt')
                    BEGIN
                        ALTER TABLE ContentRequest ADD CompletedAt DATETIME2 NULL;
                    END
                    """);
        } catch (Exception e) {
            System.err.println("Error initializing ContentRequest tables: " + e.getMessage());
        }
    }

    public void cleanupExpiredCompletedRequests() {
        try {
            // Eklendi olarak isaretlenip uzerinden 24 saat gecmis istekleri ve iliskili oylari sil
            String deleteVotesSql = """
                    DELETE FROM ContentRequestVote
                    WHERE RequestId IN (
                        SELECT Id FROM ContentRequest
                        WHERE Status IN ('COMPLETED', 'APPROVED', 'EKLENDI')
                          AND CompletedAt IS NOT NULL
                          AND CompletedAt <= DATEADD(HOUR, -24, GETUTCDATE())
                    )
                    """;
            jdbcTemplate.update(deleteVotesSql);

            String deleteRequestsSql = """
                    DELETE FROM ContentRequest
                    WHERE Status IN ('COMPLETED', 'APPROVED', 'EKLENDI')
                      AND CompletedAt IS NOT NULL
                      AND CompletedAt <= DATEADD(HOUR, -24, GETUTCDATE())
                    """;
            jdbcTemplate.update(deleteRequestsSql);
        } catch (Exception e) {
            System.err.println("Error cleaning up expired completed requests in bldizi: " + e.getMessage());
        }
    }

    public ContentRequest createRequest(UUID userId, String userNickname, String title,
                                        String contentType, String posterUrl, Integer releaseYear,
                                        Integer tmdbId, Integer tvmazeId, String description) {
        ensureTablesExist();

        UUID requestId = UUID.randomUUID();
        Instant now = Instant.now();
        Timestamp nowTs = Timestamp.from(now);

        // bldizi'den yapilan istekler her zaman IsBl = 1 ve Platform = 'bldizi' olarak kaydedilir
        String insertRequestSql = """
                INSERT INTO ContentRequest
                (Id, UserId, UserNickname, Title, ContentType, PosterUrl, ReleaseYear, TmdbId, TvMazeId, Description, Status, VoteCount, CreatedAt, IsBl, Platform)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'PENDING', 1, ?, 1, 'bldizi')
                """;

        jdbcTemplate.update(insertRequestSql, requestId, userId, userNickname, TmdbService.extractLatinNameIfPresent(title), contentType,
                posterUrl, releaseYear, tmdbId, tvmazeId, description, nowTs);

        String insertVoteSql = "INSERT INTO ContentRequestVote (RequestId, UserId, CreatedAt) VALUES (?, ?, ?)";
        try {
            jdbcTemplate.update(insertVoteSql, requestId, userId, nowTs);
        } catch (Exception ignored) {
        }

        ContentRequest req = new ContentRequest();
        req.setId(requestId);
        req.setUserId(userId);
        req.setUserNickname(userNickname);
        req.setTitle(title);
        req.setContentType(contentType);
        req.setPosterUrl(posterUrl);
        req.setReleaseYear(releaseYear);
        req.setTmdbId(tmdbId);
        req.setTvmazeId(tvmazeId);
        req.setDescription(description);
        req.setStatus("PENDING");
        req.setVoteCount(1);
        req.setCreatedAt(now);
        req.setUserVoted(true);
        req.setBl(true);
        req.setPlatform("bldizi");

        return req;
    }

    public Map<String, Object> toggleVote(UUID requestId, UUID userId) {
        ensureTablesExist();

        String checkVoteSql = "SELECT COUNT(*) FROM ContentRequestVote WHERE RequestId = ? AND UserId = ?";
        Integer voteExists = jdbcTemplate.queryForObject(checkVoteSql, Integer.class, requestId, userId);

        boolean userVoted;
        if (voteExists != null && voteExists > 0) {
            // Remove vote
            jdbcTemplate.update("DELETE FROM ContentRequestVote WHERE RequestId = ? AND UserId = ?", requestId, userId);
            jdbcTemplate.update("UPDATE ContentRequest SET VoteCount = VoteCount - 1 WHERE Id = ?", requestId);
            userVoted = false;
        } else {
            // Add vote
            jdbcTemplate.update("INSERT INTO ContentRequestVote (RequestId, UserId, CreatedAt) VALUES (?, ?, GETUTCDATE())",
                    requestId, userId);
            jdbcTemplate.update("UPDATE ContentRequest SET VoteCount = VoteCount + 1 WHERE Id = ?", requestId);
            userVoted = true;
        }

        // Check if vote count dropped to 0 or less -> delete request
        Integer currentVotes = jdbcTemplate.queryForObject("SELECT VoteCount FROM ContentRequest WHERE Id = ?",
                Integer.class, requestId);
        int finalCount = currentVotes != null ? currentVotes : 0;
        boolean deleted = false;

        if (finalCount <= 0) {
            deleteRequest(requestId);
            deleted = true;
        }

        Map<String, Object> result = new HashMap<>();
        result.put("voted", userVoted);
        result.put("voteCount", finalCount);
        result.put("deleted", deleted);
        return result;
    }

    public boolean deleteRequest(UUID requestId) {
        ensureTablesExist();
        try {
            jdbcTemplate.update("DELETE FROM ContentRequestVote WHERE RequestId = ?", requestId);
            int rows = jdbcTemplate.update("DELETE FROM ContentRequest WHERE Id = ?", requestId);
            return rows > 0;
        } catch (Exception e) {
            System.err.println("Error deleting request: " + e.getMessage());
            return false;
        }
    }

    /**
     * BL Dizi icin istekleri getirir (yalnizca BL olanlar: IsBl = 1 veya Platform = 'bldizi').
     */
    public List<ContentRequest> getRequests(String filterType, String sortBy, UUID currentUserId, int limit, int offset) {
        ensureTablesExist();
        cleanupExpiredCompletedRequests();

        StringBuilder sql = new StringBuilder();
        List<Object> params = new ArrayList<>();

        sql.append("SELECT r.Id, r.UserId, r.UserNickname, r.Title, r.ContentType, r.PosterUrl, ")
           .append("r.ReleaseYear, r.TmdbId, r.TvMazeId, r.Description, r.Status, r.VoteCount, r.CreatedAt, r.CompletedAt, ");

        if (currentUserId != null) {
            sql.append("CASE WHEN EXISTS (SELECT 1 FROM ContentRequestVote v WHERE v.RequestId = r.Id AND v.UserId = ?) THEN 1 ELSE 0 END AS UserVoted ");
            params.add(currentUserId);
        } else {
            sql.append("0 AS UserVoted ");
        }

        // bldizi'de sadece BL olan icerik istekleri listelenir
        sql.append("FROM ContentRequest r WHERE (r.IsBl = 1 OR r.Platform = 'bldizi') ");

        if ("movie".equalsIgnoreCase(filterType)) {
            sql.append("AND r.ContentType = 'movie' ");
        } else if ("series".equalsIgnoreCase(filterType)) {
            sql.append("AND r.ContentType = 'series' ");
        }

        if ("newest".equalsIgnoreCase(sortBy)) {
            sql.append("ORDER BY r.CreatedAt DESC ");
        } else {
            // Default to most votes, then newest
            sql.append("ORDER BY r.VoteCount DESC, r.CreatedAt DESC ");
        }

        sql.append("OFFSET ? ROWS FETCH NEXT ? ROWS ONLY");
        params.add(offset);
        params.add(limit);

        return jdbcTemplate.query(sql.toString(), (rs, rowNum) -> {
            ContentRequest r = new ContentRequest();
            r.setId(UUID.fromString(rs.getString("Id")));
            r.setUserId(UUID.fromString(rs.getString("UserId")));
            r.setUserNickname(rs.getString("UserNickname"));
            r.setTitle(TmdbService.extractLatinNameIfPresent(rs.getString("Title")));
            r.setContentType(rs.getString("ContentType"));
            r.setPosterUrl(rs.getString("PosterUrl"));

            int year = rs.getInt("ReleaseYear");
            r.setReleaseYear(rs.wasNull() ? null : year);

            int tmdb = rs.getInt("TmdbId");
            r.setTmdbId(rs.wasNull() ? null : tmdb);

            int tvmaze = rs.getInt("TvMazeId");
            r.setTvmazeId(rs.wasNull() ? null : tvmaze);

            r.setDescription(rs.getString("Description"));
            r.setStatus(rs.getString("Status"));
            r.setVoteCount(rs.getInt("VoteCount"));

            Timestamp ts = rs.getTimestamp("CreatedAt");
            if (ts != null) {
                r.setCreatedAt(ts.toInstant());
            }

            Timestamp compTs = rs.getTimestamp("CompletedAt");
            if (compTs != null) {
                r.setCompletedAt(compTs.toInstant());
            }

            r.setUserVoted(rs.getInt("UserVoted") == 1);
            r.setBl(true);
            r.setPlatform("bldizi");
            return r;
        }, params.toArray());
    }

    public int countRequests(String filterType) {
        ensureTablesExist();
        cleanupExpiredCompletedRequests();

        String sql = "SELECT COUNT(*) FROM ContentRequest WHERE (IsBl = 1 OR Platform = 'bldizi') ";
        if ("movie".equalsIgnoreCase(filterType)) {
            sql += "AND ContentType = 'movie'";
        } else if ("series".equalsIgnoreCase(filterType)) {
            sql += "AND ContentType = 'series'";
        }

        Integer count = jdbcTemplate.queryForObject(sql, Integer.class);
        return count != null ? count : 0;
    }

    public Optional<ContentRequest> findById(UUID requestId) {
        ensureTablesExist();
        cleanupExpiredCompletedRequests();
        String sql = "SELECT Id, UserId, UserNickname, Title, ContentType, PosterUrl, ReleaseYear, TmdbId, TvMazeId, Description, Status, VoteCount, CreatedAt, CompletedAt, 0 as UserVoted FROM ContentRequest WHERE Id = ?";
        return jdbcTemplate.query(sql, rs -> {
            if (rs.next()) {
                ContentRequest r = new ContentRequest();
                r.setId(UUID.fromString(rs.getString("Id")));
                r.setUserId(UUID.fromString(rs.getString("UserId")));
                r.setUserNickname(rs.getString("UserNickname"));
                r.setTitle(TmdbService.extractLatinNameIfPresent(rs.getString("Title")));
                r.setContentType(rs.getString("ContentType"));
                r.setPosterUrl(rs.getString("PosterUrl"));

                int year = rs.getInt("ReleaseYear");
                r.setReleaseYear(rs.wasNull() ? null : year);

                int tmdb = rs.getInt("TmdbId");
                r.setTmdbId(rs.wasNull() ? null : tmdb);

                int tvmaze = rs.getInt("TvMazeId");
                r.setTvmazeId(rs.wasNull() ? null : tvmaze);

                r.setDescription(rs.getString("Description"));
                r.setStatus(rs.getString("Status"));
                r.setVoteCount(rs.getInt("VoteCount"));

                Timestamp ts = rs.getTimestamp("CreatedAt");
                if (ts != null) {
                    r.setCreatedAt(ts.toInstant());
                }

                Timestamp compTs = rs.getTimestamp("CompletedAt");
                if (compTs != null) {
                    r.setCompletedAt(compTs.toInstant());
                }
                return Optional.of(r);
            }
            return Optional.empty();
        }, requestId);
    }
}
