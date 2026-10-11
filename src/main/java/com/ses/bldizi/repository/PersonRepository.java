package com.ses.bldizi.repository;

import com.ses.bldizi.model.Person;
import com.ses.bldizi.service.EmailService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.SQLException;
import java.util.*;

@Repository
@SuppressWarnings({"SqlResolve", "SqlNoDataSourceInspection"})
public class PersonRepository {

    private final JdbcTemplate jdbcTemplate;
    private final EmailService emailService;

    private static final String CALL_VERIFY_GENERATE_CODE = "{call VerifyOrGenerateCode(?, ?, ?, ?)}";

    public PersonRepository(JdbcTemplate jdbcTemplate, EmailService emailService) {
        this.jdbcTemplate = jdbcTemplate;
        this.emailService = emailService;
        ensureSchema();
    }

    private void ensureSchema() {
        try {
            // 1. Ensure UserSession table exists
            jdbcTemplate.execute("""
                IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = 'UserSession')
                BEGIN
                    CREATE TABLE [dbo].[UserSession] (
                        [ID] UNIQUEIDENTIFIER PRIMARY KEY DEFAULT NEWID(),
                        [UserId] UNIQUEIDENTIFIER NOT NULL,
                        [Token] UNIQUEIDENTIFIER NOT NULL,
                        [Platform] NVARCHAR(50) NOT NULL DEFAULT 'whodatidols',
                        [CreatedAt] DATETIME NOT NULL DEFAULT GETDATE(),
                        [LastActiveAt] DATETIME NOT NULL DEFAULT GETDATE()
                    );
                    CREATE INDEX [IX_UserSession_Token] ON [dbo].[UserSession]([Token]);
                    CREATE INDEX [IX_UserSession_UserId] ON [dbo].[UserSession]([UserId]);
                END
            """);

            // 2. Ensure SsoTicket table exists
            jdbcTemplate.execute("""
                IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = 'SsoTicket')
                BEGIN
                    CREATE TABLE [dbo].[SsoTicket] (
                        [ID] UNIQUEIDENTIFIER PRIMARY KEY DEFAULT NEWID(),
                        [Ticket] UNIQUEIDENTIFIER NOT NULL,
                        [UserId] UNIQUEIDENTIFIER NOT NULL,
                        [TargetPlatform] NVARCHAR(50) NULL,
                        [ExpiresAt] DATETIME NOT NULL,
                        [CreatedAt] DATETIME NOT NULL DEFAULT GETDATE()
                    );
                    CREATE INDEX [IX_SsoTicket_Ticket] ON [dbo].[SsoTicket]([Ticket]);
                END
            """);

            // 3. Ensure ResolveUserIdByCookie function exists and is up to date
            jdbcTemplate.execute("""
                CREATE OR ALTER FUNCTION [dbo].[ResolveUserIdByCookie](@cookie UNIQUEIDENTIFIER)
                RETURNS UNIQUEIDENTIFIER
                AS
                BEGIN
                    IF @cookie IS NULL RETURN NULL;
                    DECLARE @UserId UNIQUEIDENTIFIER;

                    SELECT TOP 1 @UserId = ID FROM [dbo].[Person] WHERE cookie = @cookie;

                    IF @UserId IS NULL
                    BEGIN
                        SELECT TOP 1 @UserId = UserId FROM [dbo].[UserSession] WHERE Token = @cookie;
                    END

                    RETURN @UserId;
                END;
            """);

            // 4. Ensure LoginAndSetCookie supports 3 parameters (@Platform) and multi-session
            jdbcTemplate.execute("""
                CREATE OR ALTER PROCEDURE [dbo].[LoginAndSetCookie]
                    @UsernameOrEmail NVARCHAR(255),
                    @Password NVARCHAR(255),
                    @Platform NVARCHAR(50) = 'whodatidols'
                AS
                BEGIN
                    SET NOCOUNT ON;
                    DECLARE @UserId UNIQUEIDENTIFIER, @Nickname NVARCHAR(50), @NewCookie UNIQUEIDENTIFIER, @IsBanned BIT, @BanReason NVARCHAR(500);

                    SELECT @UserId = ID, @Nickname = Nickname, @IsBanned = ISNULL(isBanned, 0), @BanReason = banReason
                    FROM [dbo].[Person]
                    WHERE (Email = @UsernameOrEmail OR Nickname = @UsernameOrEmail) AND Password = @Password;

                    IF @UserId IS NOT NULL
                    BEGIN
                        IF @IsBanned = 1
                        BEGIN
                            SELECT CAST(0 AS BIT) as Result, ISNULL(@BanReason, 'Hesabiniz askiya alinmistir') as Message;
                            RETURN;
                        END

                        SET @NewCookie = NEWID();

                        INSERT INTO [dbo].[UserSession] ([UserId], [Token], [Platform], [CreatedAt], [LastActiveAt])
                        VALUES (@UserId, @NewCookie, ISNULL(@Platform, 'whodatidols'), GETDATE(), GETDATE());

                        UPDATE [dbo].[Person] SET cookie = @NewCookie WHERE ID = @UserId;

                        DELETE FROM [dbo].[UserSession]
                        WHERE UserId = @UserId AND LastActiveAt < DATEADD(DAY, -30, GETDATE());

                        SELECT CAST(1 AS BIT) as Result, 'Giris basarili' as Message, @Nickname as Nickname, @NewCookie as Cookie;
                    END
                    ELSE
                        SELECT CAST(0 AS BIT) as Result, 'Hatali kullanici adi veya sifre' as Message;
                END;
            """);

            // 5. Ensure CreateSsoTicket procedure exists
            jdbcTemplate.execute("""
                CREATE OR ALTER PROCEDURE [dbo].[CreateSsoTicket]
                    @Cookie UNIQUEIDENTIFIER,
                    @TargetPlatform NVARCHAR(50) = NULL
                AS
                BEGIN
                    SET NOCOUNT ON;
                    DECLARE @UserId UNIQUEIDENTIFIER;
                    SET @UserId = [dbo].[ResolveUserIdByCookie](@Cookie);

                    IF @UserId IS NULL
                    BEGIN
                        SELECT CAST(0 AS BIT) AS Result, 'Gecersiz oturum' AS Message, NULL AS Ticket;
                        RETURN;
                    END

                    DECLARE @Ticket UNIQUEIDENTIFIER = NEWID();
                    INSERT INTO [dbo].[SsoTicket] ([Ticket], [UserId], [TargetPlatform], [ExpiresAt], [CreatedAt])
                    VALUES (@Ticket, @UserId, @TargetPlatform, DATEADD(SECOND, 120, GETDATE()), GETDATE());

                    DELETE FROM [dbo].[SsoTicket] WHERE ExpiresAt < GETDATE();

                    SELECT CAST(1 AS BIT) AS Result, 'Bilet olusturuldu' AS Message, @Ticket AS Ticket;
                END;
            """);

            // 6. Ensure ConsumeSsoTicket procedure exists
            jdbcTemplate.execute("""
                CREATE OR ALTER PROCEDURE [dbo].[ConsumeSsoTicket]
                    @Ticket UNIQUEIDENTIFIER,
                    @Platform NVARCHAR(50) = 'whodatidols'
                AS
                BEGIN
                    SET NOCOUNT ON;
                    DECLARE @UserId UNIQUEIDENTIFIER, @TargetPlatform NVARCHAR(50);

                    SELECT @UserId = UserId, @TargetPlatform = TargetPlatform
                    FROM [dbo].[SsoTicket]
                    WHERE Ticket = @Ticket AND ExpiresAt >= GETDATE();

                    IF @UserId IS NULL
                    BEGIN
                        SELECT CAST(0 AS BIT) AS Result, 'Gecersiz veya suresi dolmus SSO bileti' AS Message, NULL AS Nickname, NULL AS Cookie;
                        RETURN;
                    END

                    DELETE FROM [dbo].[SsoTicket] WHERE Ticket = @Ticket;

                    DECLARE @Nickname NVARCHAR(50), @IsBanned BIT, @BanReason NVARCHAR(500);
                    SELECT @Nickname = Nickname, @IsBanned = ISNULL(isBanned, 0), @BanReason = banReason
                    FROM [dbo].[Person] WHERE ID = @UserId;

                    IF @IsBanned = 1
                    BEGIN
                        SELECT CAST(0 AS BIT) AS Result, ISNULL(@BanReason, 'Hesabiniz askiya alinmistir') AS Message, NULL AS Nickname, NULL AS Cookie;
                        RETURN;
                    END

                    DECLARE @NewToken UNIQUEIDENTIFIER = NEWID();
                    INSERT INTO [dbo].[UserSession] ([UserId], [Token], [Platform], [CreatedAt], [LastActiveAt])
                    VALUES (@UserId, @NewToken, ISNULL(@Platform, 'whodatidols'), GETDATE(), GETDATE());

                    SELECT CAST(1 AS BIT) AS Result, 'SSO giris basarili' AS Message, @Nickname AS Nickname, @NewToken AS Cookie, @UserId AS UserId;
                END;
            """);
        } catch (Exception e) {
            System.err.println("PersonRepository ensureSchema error: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> loginUser(String usernameOrEmail, String password) {
        Map<String, Object> result;
        try {
            // First attempt: 3-parameter call with platform 'bldizi'
            result = jdbcTemplate.call(connection -> {
                var callableStatement = connection.prepareCall("{call LoginAndSetCookie(?, ?, 'bldizi')}");
                callableStatement.setString(1, usernameOrEmail);
                callableStatement.setString(2, password);
                return callableStatement;
            }, new ArrayList<>());
        } catch (Exception e) {
            // Fallback for SQL Server error code 8144 (too many arguments specified)
            boolean isTooManyArgs = false;
            Throwable cause = e;
            while (cause != null) {
                if (cause instanceof SQLException sqlEx && sqlEx.getErrorCode() == 8144) {
                    isTooManyArgs = true;
                    break;
                }
                if (cause.getMessage() != null && cause.getMessage().contains("8144")) {
                    isTooManyArgs = true;
                    break;
                }
                cause = cause.getCause();
            }

            if (isTooManyArgs) {
                // Call legacy 2-parameter procedure
                result = jdbcTemplate.call(connection -> {
                    var callableStatement = connection.prepareCall("{call LoginAndSetCookie(?, ?)}");
                    callableStatement.setString(1, usernameOrEmail);
                    callableStatement.setString(2, password);
                    return callableStatement;
                }, new ArrayList<>());
            } else {
                throw e;
            }
        }

        Map<String, Object> response = new HashMap<>();
        if (result.containsKey("#result-set-1")) {
            List<Map<String, Object>> resultSet = (List<Map<String, Object>>) result.get("#result-set-1");
            if (!resultSet.isEmpty()) {
                Map<String, Object> row = resultSet.getFirst();
                for (String key : row.keySet()) {
                    String lowerKey = key.toLowerCase();
                    Object value = row.get(key);
                    switch (lowerKey) {
                        case "result" -> response.put("success", value);
                        case "message" -> response.put("message", value);
                        case "nickname" -> response.put("nickname", value);
                        case "cookie" -> response.put("cookie", value != null ? value.toString() : null);
                        case "isbanned" -> response.put("isBanned", value);
                        case "banreason" -> response.put("banReason", value);
                    }
                }

                // If fallback legacy call succeeded, record token in UserSession as 'bldizi'
                Object successObj = response.get("success");
                boolean isSuccess = Boolean.TRUE.equals(successObj) || (successObj instanceof Number && ((Number) successObj).intValue() == 1);
                if (isSuccess && response.get("cookie") != null) {
                    try {
                        UUID token = UUID.fromString(response.get("cookie").toString());
                        UUID userId = findUserIdByCookie(token.toString());
                        if (userId != null) {
                            jdbcTemplate.update("""
                                IF EXISTS (SELECT 1 FROM sys.tables WHERE name = 'UserSession')
                                BEGIN
                                    INSERT INTO [dbo].[UserSession] ([UserId], [Token], [Platform], [CreatedAt], [LastActiveAt])
                                    VALUES (?, ?, 'bldizi', GETDATE(), GETDATE());
                                END
                            """, userId, token);
                        }
                    } catch (Exception ignored) {
                    }
                }

                return response;
            }
        }

        response.put("success", false);
        response.put("message", "Giriş işlemi başarısız oldu.");
        return response;
    }

    /**
     * Cleans up user session from DB (UserSession table and Person.cookie) on logout.
     */
    public void logoutUser(String cookie) {
        if (cookie == null || cookie.isBlank()) return;
        try {
            UUID token = UUID.fromString(cookie.trim());
            // 1. Delete from UserSession
            try {
                jdbcTemplate.update("DELETE FROM [dbo].[UserSession] WHERE [Token] = ?", token);
            } catch (Exception ignored) {
            }

            // 2. Clear from Person.cookie if matching
            try {
                jdbcTemplate.update("UPDATE [dbo].[Person] SET cookie = NULL WHERE cookie = ?", token);
            } catch (Exception ignored) {
            }
        } catch (Exception ignored) {
        }
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> registerUser(Person person) {
        Map<String, Object> result = jdbcTemplate.call(connection -> {
            var callableStatement = connection.prepareCall("{call RegisterUser(?, ?, ?, ?, ?)}");
            callableStatement.setString(1, person.getNickname());
            callableStatement.setString(2, person.getName());
            callableStatement.setString(3, person.getSurname());
            callableStatement.setString(4, person.getEmail());
            callableStatement.setString(5, person.getPassword());
            return callableStatement;
        }, new ArrayList<>());

        Map<String, Object> response = new HashMap<>();
        if (result.containsKey("#result-set-1")) {
            List<Map<String, Object>> resultSet = (List<Map<String, Object>>) result.get("#result-set-1");
            if (!resultSet.isEmpty()) {
                Map<String, Object> row = resultSet.getFirst();
                response.put("success", row.get("Result"));
                response.put("message", row.get("Message"));
                response.put("nickname", row.get("Nickname"));
                Object cookie = row.get("Cookie");
                response.put("cookie", cookie != null ? cookie.toString() : null);
                return response;
            }
        }

        response.put("success", false);
        response.put("message", "Kayıt işlemi tamamlanamadı.");
        return response;
    }

    public Optional<Person> findByNicknameOrEmail(String usernameOrEmail) {
        String sql = "SELECT TOP 1 ID, nickname, name, surname, email, password, isBanned, banReason, role, allowMessages, isVerified FROM [Person] WHERE nickname = ? OR email = ?";
        List<Person> list = jdbcTemplate.query(sql, (rs, rowNum) -> {
            Person p = new Person();
            p.setId(UUID.fromString(rs.getString("ID")));
            p.setNickname(rs.getString("nickname"));
            p.setName(rs.getString("name"));
            p.setSurname(rs.getString("surname"));
            p.setEmail(rs.getString("email"));
            p.setPassword(rs.getString("password"));
            p.setBanned(rs.getBoolean("isBanned"));
            p.setBanReason(rs.getString("banReason"));
            p.setRole(rs.getString("role"));
            p.setAllowMessages(rs.getBoolean("allowMessages"));
            p.setVerified(rs.getBoolean("isVerified"));
            return p;
        }, usernameOrEmail, usernameOrEmail);

        return list.isEmpty() ? Optional.empty() : Optional.of(list.getFirst());
    }

    public Map<String, Object> getUserInfoByCookie(String cookie) {
        if (cookie == null || cookie.isBlank()) return null;
        try {
            UUID cookieUuid = UUID.fromString(cookie.trim());
            String sql = """
                SELECT TOP 1 P.ID, P.nickname, P.name, P.surname, P.email, P.isBanned, P.banReason, P.role, P.allowMessages, P.isVerified
                FROM [Person] P
                WHERE P.cookie = ? OR EXISTS (
                    SELECT 1 FROM [dbo].[UserSession] US
                    WHERE US.UserId = P.ID AND US.Token = ?
                )
            """;
            return jdbcTemplate.queryForMap(sql, cookieUuid, cookieUuid);
        } catch (Exception e) {
            return null;
        }
    }

    public UUID findUserIdByCookie(String cookie) {
        if (cookie == null || cookie.isBlank()) return null;
        try {
            UUID cookieUuid = UUID.fromString(cookie.trim());
            String sql = "SELECT [dbo].[ResolveUserIdByCookie](?)";
            String userIdStr = jdbcTemplate.queryForObject(sql, String.class, cookieUuid);
            return userIdStr != null ? UUID.fromString(userIdStr) : null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Creates a short-lived SSO Ticket for cross-domain single sign-on.
     */
    @SuppressWarnings("unchecked")
    public String createSsoTicket(String cookie, String targetPlatform) {
        if (cookie == null || cookie.isBlank()) return null;
        try {
            Map<String, Object> result = jdbcTemplate.call(connection -> {
                var callableStatement = connection.prepareCall("{call CreateSsoTicket(?, ?)}");
                callableStatement.setObject(1, UUID.fromString(cookie.trim()));
                callableStatement.setString(2, targetPlatform != null ? targetPlatform : "whodatidols");
                return callableStatement;
            }, new ArrayList<>());

            if (result.containsKey("#result-set-1")) {
                List<Map<String, Object>> rows = (List<Map<String, Object>>) result.get("#result-set-1");
                if (!rows.isEmpty()) {
                    Map<String, Object> row = rows.getFirst();
                    Object res = row.get("Result");
                    boolean success = Boolean.TRUE.equals(res) || (res instanceof Number && ((Number) res).intValue() == 1);
                    if (success && row.get("Ticket") != null) {
                        return row.get("Ticket").toString();
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("createSsoTicket error: " + e.getMessage());
        }
        return null;
    }

    /**
     * Consumes an SSO Ticket, creates a platform session and returns user info + new cookie.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> consumeSsoTicket(String ticket, String platform) {
        Map<String, Object> response = new HashMap<>();
        if (ticket == null || ticket.isBlank()) {
            response.put("success", false);
            response.put("message", "Geçersiz bilet.");
            return response;
        }
        try {
            String targetPlatform = (platform != null && !platform.isBlank()) ? platform : "bldizi";
            Map<String, Object> result = jdbcTemplate.call(connection -> {
                var callableStatement = connection.prepareCall("{call ConsumeSsoTicket(?, ?)}");
                callableStatement.setObject(1, UUID.fromString(ticket.trim()));
                callableStatement.setString(2, targetPlatform);
                return callableStatement;
            }, new ArrayList<>());

            if (result.containsKey("#result-set-1")) {
                List<Map<String, Object>> rows = (List<Map<String, Object>>) result.get("#result-set-1");
                if (!rows.isEmpty()) {
                    Map<String, Object> row = rows.getFirst();
                    for (String key : row.keySet()) {
                        String lower = key.toLowerCase();
                        switch (lower) {
                            case "result" -> response.put("success", row.get(key));
                            case "message" -> response.put("message", row.get(key));
                            case "cookie" -> response.put("cookie", row.get(key) != null ? row.get(key).toString() : null);
                            case "nickname" -> response.put("nickname", row.get(key));
                            case "userid" -> response.put("userId", row.get(key));
                        }
                        response.put(key, row.get(key));
                    }
                    return response;
                }
            }
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", e.getMessage());
        }
        return response;
    }

    public Map<String, Object> updatePasswordByCookie(String cookie, String newPassword) {
        try {
            String sql = "EXEC UpdatePasswordByCookie @cookie = ?, @password = ?";
            Map<String, Object> row = jdbcTemplate.queryForMap(sql, UUID.fromString(cookie.trim()), newPassword);
            boolean result = row.containsKey("Result") && (row.get("Result").equals(1) || Boolean.TRUE.equals(row.get("Result")));
            String message = row.getOrDefault("Message", "Şifre güncellendi.").toString();
            Map<String, Object> response = new HashMap<>();
            response.put("Result", result);
            response.put("success", result);
            response.put("Message", message);
            response.put("message", message);
            return response;
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("Result", false);
            response.put("success", false);
            response.put("Message", e.getMessage());
            response.put("message", e.getMessage());
            return response;
        }
    }

    public Map<String, Object> updateProfileByCookie(String cookie, String nickname, String name, String surname, String email) {
        String sql = "EXEC UpdateUserProfile @cookie = ?, @nickname = ?, @name = ?, @surname = ?, @email = ?";

        // Before updating, check if email changed to reset verification
        try {
            Map<String, Object> currentUser = getUserInfoByCookie(cookie);
            if (currentUser != null) {
                String currentEmail = (String) currentUser.get("email");
                if (currentEmail != null && !currentEmail.equalsIgnoreCase(email)) {
                    jdbcTemplate.update("UPDATE [Person] SET isVerified = 0 WHERE ID = [dbo].[ResolveUserIdByCookie](?)",
                            UUID.fromString(cookie.trim()));
                }
            }
        } catch (Exception ignored) {
        }

        try {
            Map<String, Object> row = jdbcTemplate.queryForMap(sql, UUID.fromString(cookie.trim()), nickname, name, surname, email);
            boolean result = row.containsKey("Result") && (row.get("Result").equals(1) || Boolean.TRUE.equals(row.get("Result")));
            return Map.of("success", result, "message", row.getOrDefault("Message", "Profil güncellendi."));
        } catch (Exception e) {
            return Map.of("success", false, "message", e.getMessage());
        }
    }

    public void updateAllowMessagesByCookie(String cookie, boolean allow) {
        try {
            String sql = "UPDATE [Person] SET allowMessages = ? WHERE ID = [dbo].[ResolveUserIdByCookie](?)";
            jdbcTemplate.update(sql, allow ? 1 : 0, UUID.fromString(cookie.trim()));
        } catch (Exception ignored) {
        }
    }

    public List<Person> searchUsersForMessaging(String query, String excludeNickname) {
        String sql = "SELECT ID, nickname, name, surname, role, isVerified FROM [Person] " +
                "WHERE (nickname LIKE ? OR name LIKE ? OR surname LIKE ?) " +
                "AND (allowMessages = 1 OR allowMessages IS NULL) " +
                "AND (isBanned = 0 OR isBanned IS NULL) " +
                "AND nickname != ? " +
                "ORDER BY nickname ASC";
        String searchPattern = "%" + query + "%";
        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            Person p = new Person();
            p.setId(UUID.fromString(rs.getString("ID")));
            p.setNickname(rs.getString("nickname"));
            p.setName(rs.getString("name"));
            p.setSurname(rs.getString("surname"));
            p.setRole(rs.getString("role"));
            p.setVerified(rs.getBoolean("isVerified"));
            return p;
        }, searchPattern, searchPattern, searchPattern, excludeNickname);
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> generateResetCode(String nicknameOrEmail) {
        try {
            String code = String.format("%06d", new Random().nextInt(1000000));

            Map<String, Object> result = jdbcTemplate.call(connection -> {
                var callableStatement = connection.prepareCall(CALL_VERIFY_GENERATE_CODE);
                callableStatement.setString(1, nicknameOrEmail);
                callableStatement.setString(2, null);
                callableStatement.setString(3, null);
                callableStatement.setString(4, code);
                return callableStatement;
            }, new ArrayList<>());

            if (result.containsKey("#result-set-1")) {
                List<Map<String, Object>> resultSet = (List<Map<String, Object>>) result.get("#result-set-1");
                if (!resultSet.isEmpty()) {
                    Map<String, Object> responseMap = resultSet.getFirst();
                    boolean isSuccess = (boolean) responseMap.get("Result");

                    if (isSuccess && responseMap.containsKey("Email")) {
                        String email = (String) responseMap.get("Email");
                        if (email != null && !email.isEmpty()) {
                            try {
                                emailService.sendVerificationCode(email, code);
                            } catch (Exception e) {
                                return Map.of("Result", false, "Message", "E-posta gönderimi başarısız: " + e.getMessage());
                            }
                        }
                    }

                    return Map.of("Result", responseMap.get("Result"), "Message", responseMap.get("Message"));
                }
            }
            return Map.of("Result", false, "Message", "Bir hata oluştu");
        } catch (Exception e) {
            return Map.of("Result", false, "Message", e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> generateEmailVerificationCode(String nickname) {
        try {
            String code = String.format("%06d", new Random().nextInt(1000000));

            Map<String, Object> result = jdbcTemplate.call(connection -> {
                var callableStatement = connection.prepareCall(CALL_VERIFY_GENERATE_CODE);
                callableStatement.setString(1, nickname);
                callableStatement.setString(2, null);
                callableStatement.setString(3, null);
                callableStatement.setString(4, code);
                return callableStatement;
            }, new ArrayList<>());

            if (result.containsKey("#result-set-1")) {
                List<Map<String, Object>> resultSet = (List<Map<String, Object>>) result.get("#result-set-1");
                if (!resultSet.isEmpty()) {
                    Map<String, Object> responseMap = resultSet.getFirst();
                    boolean isSuccess = (boolean) responseMap.get("Result");

                    if (isSuccess && responseMap.containsKey("Email")) {
                        String email = (String) responseMap.get("Email");
                        if (email != null && !email.isEmpty()) {
                            try {
                                emailService.sendVerificationCode(email, code);
                            } catch (Exception e) {
                                return Map.of("success", false, "Result", false, "message", "E-posta gönderimi başarısız: " + e.getMessage());
                            }
                        }
                    }

                    Map<String, Object> cleanResponse = new HashMap<>();
                    cleanResponse.put("Result", responseMap.get("Result"));
                    cleanResponse.put("Message", responseMap.get("Message"));
                    cleanResponse.put("success", responseMap.get("Result"));
                    return cleanResponse;
                }
            }
            return Map.of("success", false, "Result", false, "message", "Bir hata oluştu.");
        } catch (Exception e) {
            return Map.of("success", false, "Result", false, "message", e.getMessage());
        }
    }

    public Map<String, Object> verifyEmailCode(String nickname, String code) {
        try {
            Map<String, Object> verifyResult = verifyResetCode(nickname, code);

            boolean isSuccess = false;
            if (verifyResult.containsKey("Result") && verifyResult.get("Result") instanceof Boolean) {
                isSuccess = (boolean) verifyResult.get("Result");
            }

            if (isSuccess) {
                jdbcTemplate.update("UPDATE [Person] SET isVerified = 1 WHERE nickname = ?",
                        nickname);
                jdbcTemplate.update(
                        "DELETE FROM [VerificationCode] WHERE ID = (SELECT ID FROM [Person] WHERE nickname = ?)",
                        nickname);

                return Map.of("success", true, "message", "Hesabınız başarıyla doğrulandı!");
            } else {
                return Map.of("success", false, "message", "Geçersiz veya süresi dolmuş kod.");
            }
        } catch (Exception e) {
            return Map.of("success", false, "message", e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> verifyResetCode(String nicknameOrEmail, String code) {
        try {
            Map<String, Object> result = jdbcTemplate.call(connection -> {
                var callableStatement = connection.prepareCall(CALL_VERIFY_GENERATE_CODE);
                callableStatement.setString(1, nicknameOrEmail);
                callableStatement.setString(2, code);
                callableStatement.setString(3, null);
                callableStatement.setString(4, null);
                return callableStatement;
            }, new ArrayList<>());

            if (result.containsKey("#result-set-1")) {
                List<Map<String, Object>> resultSet = (List<Map<String, Object>>) result.get("#result-set-1");
                if (!resultSet.isEmpty()) {
                    return resultSet.getFirst();
                }
            }
            return Map.of("Result", false, "Message", "Doğrulama başarısız");
        } catch (Exception e) {
            return Map.of("Result", false, "Message", e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> resetPassword(String nicknameOrEmail, String code, String newPassword) {
        try {
            Map<String, Object> result = jdbcTemplate.call(connection -> {
                var callableStatement = connection.prepareCall(CALL_VERIFY_GENERATE_CODE);
                callableStatement.setString(1, nicknameOrEmail);
                callableStatement.setString(2, code);
                callableStatement.setString(3, newPassword);
                callableStatement.setString(4, null);
                return callableStatement;
            }, new ArrayList<>());

            if (result.containsKey("#result-set-1")) {
                List<Map<String, Object>> resultSet = (List<Map<String, Object>>) result.get("#result-set-1");
                if (!resultSet.isEmpty()) {
                    return resultSet.getFirst();
                }
            }
            return Map.of("Result", false, "Message", "Şifre sıfırlama başarısız");
        } catch (Exception e) {
            return Map.of("Result", false, "Message", e.getMessage());
        }
    }
}
