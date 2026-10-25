package com.ses.bldizi.repository;

import com.ses.bldizi.model.PushSubscriptionModel;
import jakarta.annotation.PostConstruct;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

@Repository
@SuppressWarnings({"SqlResolve", "SqlNoDataSourceInspection"})
public class PushSubscriptionRepository {

    private final JdbcTemplate jdbcTemplate;

    public PushSubscriptionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    public void init() {
        String sql = """
                IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = 'BlDiziPushSubscriptions')
                CREATE TABLE BlDiziPushSubscriptions (
                    Id BIGINT IDENTITY(1,1) PRIMARY KEY,
                    UserNickname NVARCHAR(100) NOT NULL,
                    Endpoint NVARCHAR(1000) NOT NULL,
                    P256dh NVARCHAR(255) NOT NULL,
                    Auth NVARCHAR(255) NOT NULL,
                    CreatedAt DATETIME DEFAULT GETDATE(),
                    CONSTRAINT UQ_BlDiziPush_Endpoint UNIQUE (Endpoint)
                )
                """;
        try {
            jdbcTemplate.execute(sql);
        } catch (Exception e) {
            System.err.println("Error initializing BlDiziPushSubscriptions table: " + e.getMessage());
        }
    }

    public void saveOrUpdate(String userNickname, String endpoint, String p256dh, String auth) {
        String sql = """
                MERGE BlDiziPushSubscriptions AS target
                USING (SELECT ? AS UserNickname, ? AS Endpoint, ? AS P256dh, ? AS Auth) AS source
                ON (target.Endpoint = source.Endpoint)
                WHEN MATCHED THEN
                    UPDATE SET UserNickname = source.UserNickname, P256dh = source.P256dh, Auth = source.Auth
                WHEN NOT MATCHED THEN
                    INSERT (UserNickname, Endpoint, P256dh, Auth)
                    VALUES (source.UserNickname, source.Endpoint, source.P256dh, source.Auth);
                """;
        jdbcTemplate.update(sql, userNickname, endpoint, p256dh, auth);
    }

    public List<PushSubscriptionModel> findByNickname(String userNickname) {
        String sql = "SELECT * FROM BlDiziPushSubscriptions WHERE UserNickname = ?";
        return jdbcTemplate.query(sql, new PushSubscriptionRowMapper(), userNickname);
    }

    public void deleteByEndpoint(String endpoint) {
        String sql = "DELETE FROM BlDiziPushSubscriptions WHERE Endpoint = ?";
        jdbcTemplate.update(sql, endpoint);
    }

    public void deleteByNickname(String userNickname) {
        String sql = "DELETE FROM BlDiziPushSubscriptions WHERE UserNickname = ?";
        jdbcTemplate.update(sql, userNickname);
    }

    private static class PushSubscriptionRowMapper implements RowMapper<PushSubscriptionModel> {
        @Override
        public PushSubscriptionModel mapRow(@NonNull ResultSet rs, int rowNum) throws SQLException {
            PushSubscriptionModel model = new PushSubscriptionModel();
            model.setId(rs.getLong("Id"));
            model.setUserNickname(rs.getString("UserNickname"));
            model.setEndpoint(rs.getString("Endpoint"));
            model.setP256dh(rs.getString("P256dh"));
            model.setAuth(rs.getString("Auth"));
            java.sql.Timestamp ts = rs.getTimestamp("CreatedAt");
            if (ts != null) {
                model.setCreatedAt(ts.toLocalDateTime());
            }
            return model;
        }
    }
}