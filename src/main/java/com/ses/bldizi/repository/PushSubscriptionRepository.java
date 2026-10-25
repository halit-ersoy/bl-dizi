package com.ses.bldizi.repository;

import com.ses.bldizi.model.PushOutboxTask;
import com.ses.bldizi.model.PushPreferences;
import com.ses.bldizi.model.PushSubscription;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@SuppressWarnings({"SqlResolve", "SqlNoDataSourceInspection"})
public class PushSubscriptionRepository {

    private static final Logger logger = LoggerFactory.getLogger(PushSubscriptionRepository.class);
    private final JdbcTemplate jdbcTemplate;

    public PushSubscriptionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    public void init() {
        ensureSchema();
    }

    public synchronized void ensureSchema() {
        try {
            // 0. Drop legacy PushSubscriptions table if it had old schema (e.g. bigint Id or missing EndpointHash)
            String cleanLegacySql = """
                IF EXISTS (
                    SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS 
                    WHERE TABLE_NAME = 'BlDiziPushSubscriptions' 
                      AND ((COLUMN_NAME IN ('ID', 'Id') AND DATA_TYPE != 'uniqueidentifier')
                           OR NOT EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'BlDiziPushSubscriptions' AND COLUMN_NAME = 'EndpointHash'))
                )
                BEGIN
                    IF EXISTS (SELECT * FROM sysobjects WHERE name='BlDiziPushOutbox' AND xtype='U')
                        DROP TABLE [dbo].[BlDiziPushOutbox];
                    DROP TABLE [dbo].[BlDiziPushSubscriptions];
                END
            """;
            jdbcTemplate.execute(cleanLegacySql);

            // 1. PushSubscriptions table
            String createSubscriptionsSql = """
                IF NOT EXISTS (SELECT * FROM sysobjects WHERE name='BlDiziPushSubscriptions' AND xtype='U')
                BEGIN
                    CREATE TABLE [dbo].[BlDiziPushSubscriptions] (\n                        [ID] UNIQUEIDENTIFIER NOT NULL DEFAULT NEWID() PRIMARY KEY,
                        [UserId] UNIQUEIDENTIFIER NOT NULL,
                        [Endpoint] NVARCHAR(MAX) NOT NULL,
                        [EndpointHash] CHAR(64) NOT NULL,
                        [P256dh] VARCHAR(255) NOT NULL,
                        [Auth] VARCHAR(255) NOT NULL,
                        [UserAgent] NVARCHAR(500) NULL,
                        [IsActive] BIT NOT NULL DEFAULT 1,
                        [CreatedAt] DATETIME NOT NULL DEFAULT GETUTCDATE(),
                        [UpdatedAt] DATETIME NOT NULL DEFAULT GETUTCDATE(),
                        CONSTRAINT [FK_BlDiziPushSubscriptions_Person] FOREIGN KEY ([UserId]) REFERENCES [dbo].[Person]([ID]) ON DELETE CASCADE
                    );
                    CREATE UNIQUE NONCLUSTERED INDEX [IX_BlDiziPushSubscriptions_EndpointHash] ON [dbo].[BlDiziPushSubscriptions]([EndpointHash]);
                    CREATE NONCLUSTERED INDEX [IX_BlDiziPushSubscriptions_UserId] ON [dbo].[BlDiziPushSubscriptions]([UserId]);
                END
            """;
            jdbcTemplate.execute(createSubscriptionsSql);

            // 2. PushPreferences table
            String createPreferencesSql = """
                IF NOT EXISTS (SELECT * FROM sysobjects WHERE name='BlDiziPushPreferences' AND xtype='U')
                BEGIN
                    CREATE TABLE [dbo].[BlDiziPushPreferences] (\n                        [UserId] UNIQUEIDENTIFIER NOT NULL PRIMARY KEY,
                        [AllowMessagePush] BIT NOT NULL DEFAULT 1,
                        [AllowCommentPush] BIT NOT NULL DEFAULT 1,
                        [AllowMessagePreview] BIT NOT NULL DEFAULT 0,
                        [UpdatedAt] DATETIME NOT NULL DEFAULT GETUTCDATE(),
                        CONSTRAINT [FK_BlDiziPushPreferences_Person] FOREIGN KEY ([UserId]) REFERENCES [dbo].[Person]([ID]) ON DELETE CASCADE
                    );
                END
            """;
            jdbcTemplate.execute(createPreferencesSql);

            // 3. PushOutbox table
            String createOutboxSql = """
                IF NOT EXISTS (SELECT * FROM sysobjects WHERE name='BlDiziPushOutbox' AND xtype='U')
                BEGIN
                    CREATE TABLE [dbo].[BlDiziPushOutbox] (\n                        [ID] UNIQUEIDENTIFIER NOT NULL DEFAULT NEWID() PRIMARY KEY,
                        [SubscriptionId] UNIQUEIDENTIFIER NOT NULL,
                        [UserId] UNIQUEIDENTIFIER NOT NULL,
                        [EventType] NVARCHAR(50) NOT NULL,
                        [EventId] NVARCHAR(100) NOT NULL,
                        [IdempotencyKey] NVARCHAR(255) NOT NULL,
                        [Payload] NVARCHAR(MAX) NOT NULL,
                        [Status] NVARCHAR(20) NOT NULL DEFAULT 'PENDING',
                        [RetryCount] INT NOT NULL DEFAULT 0,
                        [MaxRetries] INT NOT NULL DEFAULT 3,
                        [NextRetryAt] DATETIME NOT NULL DEFAULT GETUTCDATE(),
                        [LastError] NVARCHAR(MAX) NULL,
                        [CreatedAt] DATETIME NOT NULL DEFAULT GETUTCDATE(),
                        [UpdatedAt] DATETIME NOT NULL DEFAULT GETUTCDATE(),
                        CONSTRAINT [FK_BlDiziPushOutbox_BlDiziPushSubscriptions] FOREIGN KEY ([SubscriptionId]) REFERENCES [dbo].[BlDiziPushSubscriptions]([ID]) ON DELETE CASCADE
                    );
                    CREATE UNIQUE NONCLUSTERED INDEX [IX_BlDiziPushOutbox_IdempotencyKey] ON [dbo].[BlDiziPushOutbox]([IdempotencyKey]);
                    CREATE NONCLUSTERED INDEX [IX_BlDiziPushOutbox_Queue] ON [dbo].[BlDiziPushOutbox]([Status], [NextRetryAt]);
                    CREATE NONCLUSTERED INDEX [IX_BlDiziPushOutbox_CreatedAt] ON [dbo].[BlDiziPushOutbox]([CreatedAt]);
                END
            """;
            jdbcTemplate.execute(createOutboxSql);

        } catch (Exception e) {
            logger.error("Push schema verification/creation error: {}", e.getMessage());
        }
    }

    public static String hashEndpoint(String endpoint) {
        if (endpoint == null) return "";
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(endpoint.trim().getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString().toUpperCase();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algoritması bulunamadı", e);
        }
    }

    public void saveOrUpdateSubscription(UUID userId, String endpoint, String p256dh, String auth, String userAgent) {
        String endpointHash = hashEndpoint(endpoint);
        Instant now = Instant.now();

        String sql = """
            IF EXISTS (SELECT 1 FROM [dbo].[BlDiziPushSubscriptions] WHERE [EndpointHash] = ?)
            BEGIN
                UPDATE [dbo].[BlDiziPushSubscriptions]
                SET [UserId] = ?,
                    [Endpoint] = ?,
                    [P256dh] = ?,
                    [Auth] = ?,
                    [UserAgent] = ?,
                    [IsActive] = 1,
                    [UpdatedAt] = ?
                WHERE [EndpointHash] = ?
            END
            ELSE
            BEGIN
                INSERT INTO [dbo].[BlDiziPushSubscriptions]
                    ([UserId], [Endpoint], [EndpointHash], [P256dh], [Auth], [UserAgent], [IsActive], [CreatedAt], [UpdatedAt])
                VALUES
                    (?, ?, ?, ?, ?, ?, 1, ?, ?)
            END
        """;

        jdbcTemplate.update(sql,
                endpointHash,
                userId, endpoint, p256dh, auth, userAgent, Timestamp.from(now), endpointHash,
                userId, endpoint, endpointHash, p256dh, auth, userAgent, Timestamp.from(now), Timestamp.from(now)
        );
    }

    public boolean deactivateSubscriptionByEndpoint(UUID userId, String endpoint) {
        String endpointHash = hashEndpoint(endpoint);
        String sql = "UPDATE [dbo].[BlDiziPushSubscriptions] SET [IsActive] = 0, [UpdatedAt] = GETUTCDATE() WHERE [EndpointHash] = ? AND [UserId] = ?";
        int updated = jdbcTemplate.update(sql, endpointHash, userId);
        return updated > 0;
    }

    public void deactivateSubscriptionById(UUID subscriptionId) {
        String sql = "UPDATE [dbo].[BlDiziPushSubscriptions] SET [IsActive] = 0, [UpdatedAt] = GETUTCDATE() WHERE [ID] = ?";
        jdbcTemplate.update(sql, subscriptionId);
    }

    public List<PushSubscription> findActiveSubscriptionsByUserId(UUID userId) {
        String sql = "SELECT * FROM [dbo].[BlDiziPushSubscriptions] WHERE [UserId] = ? AND [IsActive] = 1";
        return jdbcTemplate.query(sql, new PushSubscriptionRowMapper(), userId);
    }

    public Optional<PushSubscription> findSubscriptionById(UUID subscriptionId) {
        String sql = "SELECT * FROM [dbo].[BlDiziPushSubscriptions] WHERE [ID] = ?";
        List<PushSubscription> list = jdbcTemplate.query(sql, new PushSubscriptionRowMapper(), subscriptionId);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    public PushPreferences getPreferences(UUID userId) {
        String sql = "SELECT * FROM [dbo].[BlDiziPushPreferences] WHERE [UserId] = ?";
        List<PushPreferences> list = jdbcTemplate.query(sql, (rs, rowNum) -> {
            PushPreferences prefs = new PushPreferences();
            prefs.setUserId(UUID.fromString(rs.getString("UserId")));
            prefs.setAllowMessagePush(rs.getBoolean("AllowMessagePush"));
            prefs.setAllowCommentPush(rs.getBoolean("AllowCommentPush"));
            prefs.setAllowMessagePreview(rs.getBoolean("AllowMessagePreview"));
            Timestamp ts = rs.getTimestamp("UpdatedAt");
            if (ts != null) prefs.setUpdatedAt(ts.toInstant());
            return prefs;
        }, userId);

        if (!list.isEmpty()) {
            return list.get(0);
        }
        return new PushPreferences(userId);
    }

    public void savePreferences(UUID userId, boolean allowMessagePush, boolean allowCommentPush, boolean allowMessagePreview) {
        String sql = """
            IF EXISTS (SELECT 1 FROM [dbo].[BlDiziPushPreferences] WHERE [UserId] = ?)
            BEGIN
                UPDATE [dbo].[BlDiziPushPreferences]
                SET [AllowMessagePush] = ?,
                    [AllowCommentPush] = ?,
                    [AllowMessagePreview] = ?,
                    [UpdatedAt] = GETUTCDATE()
                WHERE [UserId] = ?
            END
            ELSE
            BEGIN
                INSERT INTO [dbo].[BlDiziPushPreferences]
                    ([UserId], [AllowMessagePush], [AllowCommentPush], [AllowMessagePreview], [UpdatedAt])
                VALUES
                    (?, ?, ?, ?, GETUTCDATE())
            END
        """;
        jdbcTemplate.update(sql,
                userId,
                allowMessagePush ? 1 : 0, allowCommentPush ? 1 : 0, allowMessagePreview ? 1 : 0, userId,
                userId, allowMessagePush ? 1 : 0, allowCommentPush ? 1 : 0, allowMessagePreview ? 1 : 0
        );
    }

    public boolean enqueueOutboxTask(UUID subscriptionId, UUID userId, String eventType, String eventId, String idempotencyKey, String payloadJson) {
        String sql = """
            IF NOT EXISTS (SELECT 1 FROM [dbo].[BlDiziPushOutbox] WHERE [IdempotencyKey] = ?)
            BEGIN
                INSERT INTO [dbo].[BlDiziPushOutbox]
                    ([SubscriptionId], [UserId], [EventType], [EventId], [IdempotencyKey], [Payload], [Status], [RetryCount], [MaxRetries], [NextRetryAt], [CreatedAt], [UpdatedAt])
                VALUES
                    (?, ?, ?, ?, ?, ?, 'PENDING', 0, 3, GETUTCDATE(), GETUTCDATE(), GETUTCDATE())
            END
        """;
        int affected = jdbcTemplate.update(sql, idempotencyKey, subscriptionId, userId, eventType, eventId, idempotencyKey, payloadJson);
        return affected > 0;
    }

    public List<PushOutboxTask> fetchPendingTasks(int limit) {
        String sql = "SELECT TOP (?) * FROM [dbo].[BlDiziPushOutbox] WHERE [Status] = 'PENDING' AND [NextRetryAt] <= GETUTCDATE() ORDER BY [NextRetryAt] ASC";
        return jdbcTemplate.query(sql, new PushOutboxTaskRowMapper(), limit);
    }

    public void markTaskProcessing(UUID taskId) {
        String sql = "UPDATE [dbo].[BlDiziPushOutbox] SET [Status] = 'PROCESSING', [UpdatedAt] = GETUTCDATE() WHERE [ID] = ? AND [Status] = 'PENDING'";
        jdbcTemplate.update(sql, taskId);
    }

    public void markTaskCompleted(UUID taskId) {
        String sql = "UPDATE [dbo].[BlDiziPushOutbox] SET [Status] = 'COMPLETED', [UpdatedAt] = GETUTCDATE() WHERE [ID] = ?";
        jdbcTemplate.update(sql, taskId);
    }

    public void markTaskFailed(UUID taskId, String error, boolean canRetry, int delaySeconds) {
        if (canRetry) {
            String sql = "UPDATE [dbo].[BlDiziPushOutbox] SET [Status] = 'PENDING', [RetryCount] = [RetryCount] + 1, [NextRetryAt] = DATEADD(SECOND, ?, GETUTCDATE()), [LastError] = ?, [UpdatedAt] = GETUTCDATE() WHERE [ID] = ?";
            jdbcTemplate.update(sql, delaySeconds, error, taskId);
        } else {
            String sql = "UPDATE [dbo].[BlDiziPushOutbox] SET [Status] = 'FAILED', [LastError] = ?, [UpdatedAt] = GETUTCDATE() WHERE [ID] = ?";
            jdbcTemplate.update(sql, error, taskId);
        }
    }

    public int cleanupOldOutbox(int daysToKeep) {
        String sql = "DELETE FROM [dbo].[BlDiziPushOutbox] WHERE [Status] IN ('COMPLETED', 'FAILED') AND [CreatedAt] < DATEADD(DAY, -?, GETUTCDATE())";
        return jdbcTemplate.update(sql, daysToKeep);
    }

    private static class PushSubscriptionRowMapper implements RowMapper<PushSubscription> {
        @Override
        public PushSubscription mapRow(@org.springframework.lang.NonNull ResultSet rs, int rowNum) throws SQLException {
            PushSubscription sub = new PushSubscription();
            sub.setId(UUID.fromString(rs.getString("ID")));
            sub.setUserId(UUID.fromString(rs.getString("UserId")));
            sub.setEndpoint(rs.getString("Endpoint"));
            sub.setEndpointHash(rs.getString("EndpointHash"));
            sub.setP256dh(rs.getString("P256dh"));
            sub.setAuth(rs.getString("Auth"));
            sub.setUserAgent(rs.getString("UserAgent"));
            sub.setActive(rs.getBoolean("IsActive"));
            Timestamp ct = rs.getTimestamp("CreatedAt");
            if (ct != null) sub.setCreatedAt(ct.toInstant());
            Timestamp ut = rs.getTimestamp("UpdatedAt");
            if (ut != null) sub.setUpdatedAt(ut.toInstant());
            return sub;
        }
    }

    private static class PushOutboxTaskRowMapper implements RowMapper<PushOutboxTask> {
        @Override
        public PushOutboxTask mapRow(@org.springframework.lang.NonNull ResultSet rs, int rowNum) throws SQLException {
            PushOutboxTask task = new PushOutboxTask();
            task.setId(UUID.fromString(rs.getString("ID")));
            task.setSubscriptionId(UUID.fromString(rs.getString("SubscriptionId")));
            task.setUserId(UUID.fromString(rs.getString("UserId")));
            task.setEventType(rs.getString("EventType"));
            task.setEventId(rs.getString("EventId"));
            task.setIdempotencyKey(rs.getString("IdempotencyKey"));
            task.setPayload(rs.getString("Payload"));
            task.setStatus(rs.getString("Status"));
            task.setRetryCount(rs.getInt("RetryCount"));
            task.setMaxRetries(rs.getInt("MaxRetries"));
            Timestamp nrt = rs.getTimestamp("NextRetryAt");
            if (nrt != null) task.setNextRetryAt(nrt.toInstant());
            task.setLastError(rs.getString("LastError"));
            Timestamp ct = rs.getTimestamp("CreatedAt");
            if (ct != null) task.setCreatedAt(ct.toInstant());
            Timestamp ut = rs.getTimestamp("UpdatedAt");
            if (ut != null) task.setUpdatedAt(ut.toInstant());
            return task;
        }
    }

    public List<PushSubscription> findActiveSubscriptionsByNickname(String nickname) {
        if (nickname == null || nickname.isBlank()) return List.of();
        String sql = """
            SELECT s.* FROM [dbo].[BlDiziPushSubscriptions] s
            INNER JOIN [dbo].[Person] p ON s.UserId = p.ID
            WHERE p.nickname = ? AND s.IsActive = 1
        """;
        return jdbcTemplate.query(sql, new PushSubscriptionRowMapper(), nickname.trim());
    }

    public PushPreferences getPreferencesByNickname(String nickname) {
        if (nickname == null || nickname.isBlank()) return new PushPreferences();
        String sql = """
            SELECT pref.* FROM [dbo].[BlDiziPushPreferences] pref
            INNER JOIN [dbo].[Person] p ON pref.UserId = p.ID
            WHERE p.nickname = ?
        """;
        List<PushPreferences> list = jdbcTemplate.query(sql, (rs, rowNum) -> {
            PushPreferences p = new PushPreferences();
            p.setUserId((UUID) rs.getObject("UserId"));
            p.setAllowMessagePush(rs.getBoolean("AllowMessagePush"));
            p.setAllowCommentPush(rs.getBoolean("AllowCommentPush"));
            p.setAllowMessagePreview(rs.getBoolean("AllowMessagePreview"));
            Timestamp ts = rs.getTimestamp("UpdatedAt");
            if (ts != null) p.setUpdatedAt(ts.toInstant());
            return p;
        }, nickname.trim());
        return list.isEmpty() ? new PushPreferences() : list.get(0);
    }

    public void deleteByEndpoint(String endpoint) {
        if (endpoint == null) return;
        String endpointHash = hashEndpoint(endpoint);
        jdbcTemplate.update("UPDATE [dbo].[BlDiziPushSubscriptions] SET [IsActive] = 0, [UpdatedAt] = GETUTCDATE() WHERE [EndpointHash] = ?", endpointHash);
    }

}
