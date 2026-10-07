package com.ses.bldizi.controller;

import com.ses.bldizi.model.Person;
import com.ses.bldizi.repository.PersonRepository;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@RestController
public class UserInteractionController {

    private final PersonRepository personRepository;

    @Value("${media.profile.images.path}")
    private String profileImagesPath;

    public UserInteractionController(PersonRepository personRepository) {
        this.personRepository = personRepository;
    }

    private String resolveCookie(String cookie, HttpServletRequest request) {
        if (cookie != null && !cookie.trim().isEmpty()) {
            return cookie.trim();
        }
        if (request != null) {
            Cookie[] cookies = request.getCookies();
            if (cookies != null) {
                for (Cookie c : cookies) {
                    if ("wdiAuth".equalsIgnoreCase(c.getName()) && c.getValue() != null && !c.getValue().isEmpty()) {
                        return c.getValue().trim();
                    }
                }
            }
            String authHeader = request.getHeader("Authorization");
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                return authHeader.substring(7).trim();
            }
            String xAuth = request.getHeader("X-Auth-Token");
            if (xAuth != null && !xAuth.trim().isEmpty()) {
                return xAuth.trim();
            }
        }
        return null;
    }

    @GetMapping("/api/user/profile")
    public ResponseEntity<?> getProfile(@CookieValue(name = "wdiAuth", required = false) String cookie,
                                        HttpServletRequest request) {
        String token = resolveCookie(cookie, request);
        if (token == null) {
            return ResponseEntity.status(401).body(Map.of("success", false, "message", "Oturum açılmamış."));
        }

        try {
            Map<String, Object> userInfo = personRepository.getUserInfoByCookie(token);
            if (userInfo == null || userInfo.isEmpty()) {
                return ResponseEntity.status(401).body(Map.of("success", false, "message", "Kullanıcı bulunamadı."));
            }
            return ResponseEntity.ok(userInfo);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @PostMapping("/api/user/update-profile")
    public ResponseEntity<?> updateProfile(@CookieValue(name = "wdiAuth", required = false) String cookie,
                                           @RequestBody Map<String, String> requestData,
                                           HttpServletRequest request) {
        String token = resolveCookie(cookie, request);
        if (token == null) {
            return ResponseEntity.status(401).body(Map.of("success", false, "message", "Unauthorized"));
        }

        String newNickname = requestData.get("nickname");
        String newName = requestData.get("name");
        String newSurname = requestData.get("surname");
        String newEmail = requestData.get("email");

        if (newNickname == null || newNickname.trim().isEmpty() ||
                newName == null || newName.trim().isEmpty() ||
                newSurname == null || newSurname.trim().isEmpty() ||
                newEmail == null || newEmail.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Lütfen tüm zorunlu alanları doldurunuz."));
        }

        newNickname = newNickname.trim();
        newName = newName.trim();
        newSurname = newSurname.trim();
        newEmail = newEmail.trim();

        if (!newNickname.matches("^[a-zA-Z0-9_.]+$")) {
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message",
                            "Kullanıcı adı sadece harf, rakam, alt çizgi (_) ve nokta (.) içerebilir."));
        }

        try {
            Map<String, Object> currentInfo = personRepository.getUserInfoByCookie(token);
            if (currentInfo == null) {
                return ResponseEntity.status(401).body(Map.of("success", false, "message", "Kullanıcı bulunamadı."));
            }

            String currentNickname = (String) currentInfo.get("nickname");
            String currentEmail = (String) currentInfo.get("email");

            if (currentNickname != null && !newNickname.equalsIgnoreCase(currentNickname)) {
                Optional<Person> existing = personRepository.findByNicknameOrEmail(newNickname);
                if (existing.isPresent()) {
                    return ResponseEntity.badRequest()
                            .body(Map.of("success", false, "message", "Bu kullanıcı adı zaten kullanımda."));
                }
            }

            if (currentEmail != null && !newEmail.equalsIgnoreCase(currentEmail)) {
                Optional<Person> existing = personRepository.findByNicknameOrEmail(newEmail);
                if (existing.isPresent()) {
                    return ResponseEntity.badRequest()
                            .body(Map.of("success", false, "message", "Bu e-posta adresi zaten kullanımda."));
                }
            }

            Map<String, Object> result = personRepository.updateProfileByCookie(token, newNickname, newName, newSurname, newEmail);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @PostMapping("/api/user/update-password")
    public ResponseEntity<?> updatePassword(@CookieValue(name = "wdiAuth", required = false) String cookie,
                                            @RequestBody Map<String, String> requestData,
                                            HttpServletRequest request) {
        String token = resolveCookie(cookie, request);
        if (token == null) {
            return ResponseEntity.status(401).body(Map.of("success", false, "Result", false, "message", "Unauthorized", "Message", "Unauthorized"));
        }

        String newPassword = requestData != null ? requestData.get("newPassword") : null;
        if (newPassword == null || newPassword.length() < 6) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "Result", false, "message", "Şifre en az 6 karakter olmalıdır.", "Message", "Şifre en az 6 karakter olmalıdır."));
        }

        Map<String, Object> result = personRepository.updatePasswordByCookie(token, newPassword);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/api/user/request-verification")
    public ResponseEntity<?> requestVerification(@CookieValue(name = "wdiAuth", required = false) String cookie,
                                                 HttpServletRequest request) {
        String token = resolveCookie(cookie, request);
        if (token == null) {
            return ResponseEntity.status(401).body(Map.of("success", false, "message", "Unauthorized"));
        }

        try {
            Map<String, Object> userInfo = personRepository.getUserInfoByCookie(token);
            if (userInfo == null) {
                return ResponseEntity.status(401).body(Map.of("success", false, "message", "Kullanıcı bulunamadı."));
            }

            String email = (String) userInfo.get("email");
            if (email == null || email.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("success", false, "message", "E-posta adresi bulunamadı."));
            }

            Map<String, Object> result = personRepository.generateEmailVerificationCode((String) userInfo.get("nickname"));
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @PostMapping("/api/user/verify-email")
    public ResponseEntity<?> verifyEmail(@CookieValue(name = "wdiAuth", required = false) String cookie,
                                         @RequestBody Map<String, String> requestData,
                                         HttpServletRequest request) {
        String token = resolveCookie(cookie, request);
        if (token == null) {
            return ResponseEntity.status(401).body(Map.of("success", false, "message", "Unauthorized"));
        }

        try {
            String code = requestData.get("code");
            if (code == null || code.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Kod gerekli."));
            }

            Map<String, Object> userInfo = personRepository.getUserInfoByCookie(token);
            if (userInfo == null) {
                return ResponseEntity.status(401).body(Map.of("success", false, "message", "Kullanıcı bulunamadı."));
            }

            String nickname = (String) userInfo.get("nickname");
            Map<String, Object> result = personRepository.verifyEmailCode(nickname, code.trim());
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @PostMapping("/api/user/upload-profile-photo")
    public ResponseEntity<?> uploadProfilePhoto(@CookieValue(name = "wdiAuth", required = false) String cookie,
                                                @RequestParam("file") MultipartFile file,
                                                HttpServletRequest request) {
        String token = resolveCookie(cookie, request);
        if (token == null) {
            return ResponseEntity.status(401).body(Map.of("success", false, "message", "Oturum süresi dolmuş."));
        }

        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Dosya seçilmedi."));
        }

        try {
            Map<String, Object> userInfo = personRepository.getUserInfoByCookie(token);
            if (userInfo == null || !userInfo.containsKey("ID")) {
                return ResponseEntity.status(401).body(Map.of("success", false, "message", "Kullanıcı bulunamadı."));
            }

            String userId = userInfo.get("ID").toString();
            BufferedImage originalImage = ImageIO.read(file.getInputStream());

            if (originalImage == null) {
                return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Geçersiz resim formatı."));
            }

            int width = originalImage.getWidth();
            int height = originalImage.getHeight();

            if (width < 300 || height < 300) {
                return ResponseEntity.badRequest()
                        .body(Map.of("success", false, "message", "Resim boyutu en az 300x300 olmalıdır."));
            }

            BufferedImage finalImage = originalImage;
            if (width > 500 || height > 500) {
                int newWidth = 500;
                int newHeight = 500;
                Image resultingImage = originalImage.getScaledInstance(newWidth, newHeight, Image.SCALE_SMOOTH);
                finalImage = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_RGB);
                Graphics2D g2d = finalImage.createGraphics();
                g2d.drawImage(resultingImage, 0, 0, null);
                g2d.dispose();
            }

            Path uploadDir = Paths.get(profileImagesPath).toAbsolutePath().normalize();
            Files.createDirectories(uploadDir);

            Path targetLocation = uploadDir.resolve(userId + ".jpg");
            ImageIO.write(finalImage, "jpg", targetLocation.toFile());

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Profil fotoğrafı güncellendi.",
                    "imageUrl", "/media/profile/" + userId + "?t=" + System.currentTimeMillis()));

        } catch (Exception e) {
            return ResponseEntity.status(500)
                    .body(Map.of("success", false, "message", "Yükleme sırasında bir hata oluştu: " + e.getMessage()));
        }
    }

    @DeleteMapping("/api/user/remove-profile-photo")
    public ResponseEntity<?> removeProfilePhoto(@CookieValue(name = "wdiAuth", required = false) String cookie,
                                                HttpServletRequest request) {
        String token = resolveCookie(cookie, request);
        if (token == null) {
            return ResponseEntity.status(401).body(Map.of("success", false, "message", "Unauthorized"));
        }

        try {
            Map<String, Object> userInfo = personRepository.getUserInfoByCookie(token);
            if (userInfo == null || !userInfo.containsKey("ID")) {
                return ResponseEntity.status(401).body(Map.of("success", false, "message", "Kullanıcı bulunamadı."));
            }
            String userId = userInfo.get("ID").toString();

            Path uploadDir = Paths.get(profileImagesPath).toAbsolutePath().normalize();
            Path targetLocation = uploadDir.resolve(userId + ".jpg");

            if (Files.exists(targetLocation)) {
                Files.delete(targetLocation);
            }

            return ResponseEntity.ok(Map.of("success", true, "message", "Fotoğraf kaldırıldı."));
        } catch (Exception e) {
            return ResponseEntity.status(500)
                    .body(Map.of("success", false, "message", "Silme sırasında bir hata oluştu: " + e.getMessage()));
        }
    }

}
