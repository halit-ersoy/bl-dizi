package com.ses.bldizi.repository;

import com.ses.bldizi.model.Person;
import com.ses.bldizi.service.EmailService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.SqlParameter;
import org.springframework.stereotype.Repository;

import java.util.*;

@Repository
public class PersonRepository {

    private final JdbcTemplate jdbcTemplate;
    private final EmailService emailService;

    private static final String CALL_VERIFY_GENERATE_CODE = "{call VerifyOrGenerateCode(?, ?, ?, ?)}";

    public PersonRepository(JdbcTemplate jdbcTemplate, EmailService emailService) {
        this.jdbcTemplate = jdbcTemplate;
        this.emailService = emailService;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> loginUser(String usernameOrEmail, String password) {
        Map<String, Object> result = jdbcTemplate.call(connection -> {
            var callableStatement = connection.prepareCall("{call LoginAndSetCookie(?, ?)}");
            callableStatement.setString(1, usernameOrEmail);
            callableStatement.setString(2, password);
            return callableStatement;
        }, new ArrayList<SqlParameter>());

        Map<String, Object> response = new HashMap<>();
        if (result.containsKey("#result-set-1")) {
            List<Map<String, Object>> resultSet = (List<Map<String, Object>>) result.get("#result-set-1");
            if (!resultSet.isEmpty()) {
                Map<String, Object> row = resultSet.get(0);
                for (String key : row.keySet()) {
                    String lowerKey = key.toLowerCase();
                    Object value = row.get(key);
                    if (lowerKey.equals("result")) {
                        response.put("success", value);
                    } else if (lowerKey.equals("message")) {
                        response.put("message", value);
                    } else if (lowerKey.equals("nickname")) {
                        response.put("nickname", value);
                    } else if (lowerKey.equals("cookie")) {
                        response.put("cookie", value != null ? value.toString() : null);
                    } else if (lowerKey.equals("isbanned")) {
                        response.put("isBanned", value);
                    } else if (lowerKey.equals("banreason")) {
                        response.put("banReason", value);
                    }
                }
                return response;
            }
        }

        response.put("success", false);
        response.put("message", "Giriş işlemi başarısız oldu.");
        return response;
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
        }, new ArrayList<SqlParameter>());

        Map<String, Object> response = new HashMap<>();
        if (result.containsKey("#result-set-1")) {
            List<Map<String, Object>> resultSet = (List<Map<String, Object>>) result.get("#result-set-1");
            if (!resultSet.isEmpty()) {
                Map<String, Object> row = resultSet.get(0);
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

        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    public Map<String, Object> getUserInfoByCookie(String cookie) {
        try {
            UUID cookieUuid = UUID.fromString(cookie);
            String sql = "SELECT TOP 1 ID, nickname, name, surname, email, isBanned, banReason, role, allowMessages, isVerified FROM [Person] WHERE cookie = ?";
            return jdbcTemplate.queryForMap(sql, cookieUuid);
        } catch (Exception e) {
            return null;
        }
    }

    public Map<String, Object> updatePasswordByCookie(String cookie, String newPassword) {
        try {
            String sql = "EXEC UpdatePasswordByCookie @cookie = ?, @password = ?";
            Map<String, Object> row = jdbcTemplate.queryForMap(sql, UUID.fromString(cookie), newPassword);
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
                    jdbcTemplate.update("UPDATE [Person] SET isVerified = 0 WHERE cookie = ?",
                            UUID.fromString(cookie));
                }
            }
        } catch (Exception ignored) {
        }

        try {
            Map<String, Object> row = jdbcTemplate.queryForMap(sql, UUID.fromString(cookie), nickname, name, surname, email);
            boolean result = row.containsKey("Result") && (row.get("Result").equals(1) || Boolean.TRUE.equals(row.get("Result")));
            return Map.of("success", result, "message", row.getOrDefault("Message", "Profil güncellendi."));
        } catch (Exception e) {
            return Map.of("success", false, "message", e.getMessage());
        }
    }

    public void updateAllowMessagesByCookie(String cookie, boolean allow) {
        try {
            String sql = "UPDATE [Person] SET allowMessages = ? WHERE cookie = ?";
            jdbcTemplate.update(sql, allow ? 1 : 0, UUID.fromString(cookie));
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
            }, new ArrayList<SqlParameter>());

            if (result.containsKey("#result-set-1")) {
                List<Map<String, Object>> resultSet = (List<Map<String, Object>>) result.get("#result-set-1");
                if (!resultSet.isEmpty()) {
                    Map<String, Object> responseMap = resultSet.get(0);
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
            }, new ArrayList<SqlParameter>());

            if (result.containsKey("#result-set-1")) {
                List<Map<String, Object>> resultSet = (List<Map<String, Object>>) result.get("#result-set-1");
                if (!resultSet.isEmpty()) {
                    Map<String, Object> responseMap = resultSet.get(0);
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
            }, new ArrayList<SqlParameter>());

            if (result.containsKey("#result-set-1")) {
                List<Map<String, Object>> resultSet = (List<Map<String, Object>>) result.get("#result-set-1");
                if (!resultSet.isEmpty()) {
                    return resultSet.get(0);
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
            }, new ArrayList<SqlParameter>());

            if (result.containsKey("#result-set-1")) {
                List<Map<String, Object>> resultSet = (List<Map<String, Object>>) result.get("#result-set-1");
                if (!resultSet.isEmpty()) {
                    return resultSet.get(0);
                }
            }
            return Map.of("Result", false, "Message", "Şifre sıfırlama başarısız");
        } catch (Exception e) {
            return Map.of("Result", false, "Message", e.getMessage());
        }
    }
}
