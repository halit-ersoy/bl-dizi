package com.ses.bldizi.controller;

import com.ses.bldizi.model.Person;
import com.ses.bldizi.repository.PersonRepository;
import com.ses.bldizi.service.SystemSettingService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@RestController
public class AuthController {

    private final PersonRepository personRepository;
    private final SystemSettingService systemSettingService;

    public AuthController(PersonRepository personRepository, SystemSettingService systemSettingService) {
        this.personRepository = personRepository;
        this.systemSettingService = systemSettingService;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> loginRequest,
                                   HttpServletRequest request,
                                   HttpServletResponse response) {
        try {
            String usernameOrEmail = loginRequest.get("usernameOrEmail");
            if (usernameOrEmail == null || usernameOrEmail.trim().isEmpty()) {
                usernameOrEmail = loginRequest.get("email");
            }
            String password = loginRequest.get("password");

            if (usernameOrEmail == null || usernameOrEmail.trim().isEmpty() || password == null || password.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "message", "Kullanıcı adı ve şifre gereklidir."
                ));
            }

            Map<String, Object> result = personRepository.loginUser(usernameOrEmail.trim(), password);

            Object successObj = result.get("success");
            boolean isSuccess = false;
            if (successObj instanceof Boolean) {
                isSuccess = (Boolean) successObj;
            } else if (successObj instanceof Number) {
                isSuccess = ((Number) successObj).intValue() == 1;
            }

            if (isSuccess) {
                Object cookieObj = result.get("cookie");
                if (cookieObj != null) {
                    Cookie authCookie = new Cookie("wdiAuth", cookieObj.toString());
                    authCookie.setHttpOnly(true);
                    authCookie.setPath("/");
                    authCookie.setMaxAge(7 * 24 * 60 * 60);
                    if (request.isSecure()) {
                        authCookie.setSecure(true);
                    }
                    response.addCookie(authCookie);
                }
                return ResponseEntity.ok(result);
            } else {
                return ResponseEntity.status(401).body(result);
            }
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "message", "Giriş sırasında hata oluştu: " + e.getMessage()
            ));
        }
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody Person person,
                                      HttpServletRequest request,
                                      HttpServletResponse response) {
        try {
            if (!systemSettingService.isRegistrationEnabled()) {
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "message", "Şu anda yeni üye alımı kapalıdır."
                ));
            }

            // 1. Mandatory Fields Validation
            if (person.getName() == null || person.getName().trim().isEmpty() ||
                    person.getSurname() == null || person.getSurname().trim().isEmpty() ||
                    person.getNickname() == null || person.getNickname().trim().isEmpty() ||
                    person.getEmail() == null || person.getEmail().trim().isEmpty() ||
                    person.getPassword() == null || person.getPassword().trim().isEmpty()) {

                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "message", "Lütfen tüm zorunlu alanları doldurunuz."
                ));
            }

            // 2. Password Length Validation
            if (person.getPassword().length() < 6) {
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "message", "Şifre en az 6 karakter olmalıdır."
                ));
            }

            // 3. Email Format Validation
            if (!person.getEmail().matches("^[A-Za-z0-9+_.-]+@(.+)$")) {
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "message", "Geçersiz e-posta formatı!"
                ));
            }

            // 4. Username Validation
            if (!person.getNickname().matches("^[a-zA-Z0-9_.]+$")) {
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "message", "Kullanıcı adı sadece harf, rakam, alt çizgi (_) ve nokta (.) içerebilir."
                ));
            }

            // 5. Duplicate Check
            Optional<Person> existingUser = personRepository.findByNicknameOrEmail(person.getNickname().trim());
            if (existingUser.isPresent()) {
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "message", "Bu kullanıcı adı zaten kullanımda."
                ));
            }

            existingUser = personRepository.findByNicknameOrEmail(person.getEmail().trim());
            if (existingUser.isPresent()) {
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "message", "Bu e-posta adresi zaten kullanımda."
                ));
            }

            // 6. Register
            Map<String, Object> result = personRepository.registerUser(person);
            Object successObj = result.get("success");
            boolean isSuccess = false;
            if (successObj instanceof Boolean) {
                isSuccess = (Boolean) successObj;
            } else if (successObj instanceof Number) {
                isSuccess = ((Number) successObj).intValue() == 1;
            }

            if (isSuccess) {
                Object cookieObj = result.get("cookie");
                if (cookieObj != null) {
                    Cookie authCookie = new Cookie("wdiAuth", cookieObj.toString());
                    authCookie.setHttpOnly(true);
                    authCookie.setPath("/");
                    authCookie.setMaxAge(7 * 24 * 60 * 60);
                    if (request.isSecure()) {
                        authCookie.setSecure(true);
                    }
                    response.addCookie(authCookie);
                }
                return ResponseEntity.ok(result);
            } else {
                return ResponseEntity.badRequest().body(result);
            }
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Kayıt hatası: " + e.getMessage()
            ));
        }
    }

    @RequestMapping(value = {"/logout", "/api/user/logout"}, method = {RequestMethod.GET, RequestMethod.POST})
    public ResponseEntity<?> logout(HttpServletResponse response) {
        Cookie authCookie = new Cookie("wdiAuth", "");
        authCookie.setHttpOnly(true);
        authCookie.setPath("/");
        authCookie.setMaxAge(0);
        response.addCookie(authCookie);

        return ResponseEntity.ok(Map.of("success", true, "message", "Çıkış yapıldı."));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(@RequestBody Map<String, String> request) {
        try {
            String usernameOrEmail = request.get("usernameOrEmail");
            if (usernameOrEmail == null || usernameOrEmail.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "message", "Kullanıcı adı veya e-posta adresi gerekli."
                ));
            }

            Map<String, Object> result = personRepository.generateResetCode(usernameOrEmail.trim());
            boolean isSuccess = Boolean.TRUE.equals(result.get("Result"));

            if (isSuccess) {
                return ResponseEntity.ok(Map.of(
                        "success", true,
                        "message", "Doğrulama kodu e-posta adresinize gönderildi."
                ));
            } else {
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "message", result.getOrDefault("Message", "Kullanıcı bulunamadı.")
                ));
            }
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", e.getMessage()
            ));
        }
    }

    @PostMapping("/verify-code")
    public ResponseEntity<?> verifyCode(@RequestBody Map<String, String> request) {
        try {
            String usernameOrEmail = request.get("usernameOrEmail");
            String code = request.get("code");

            Map<String, Object> result = personRepository.verifyResetCode(usernameOrEmail, code);
            boolean isSuccess = Boolean.TRUE.equals(result.get("Result"));

            return ResponseEntity.ok(Map.of(
                    "success", isSuccess,
                    "message", result.getOrDefault("Message", isSuccess ? "Kod doğrulandı." : "Geçersiz kod.")
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", e.getMessage()
            ));
        }
    }

    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@RequestBody Map<String, String> request) {
        try {
            String usernameOrEmail = request.get("usernameOrEmail");
            String code = request.get("code");
            String newPassword = request.get("newPassword");

            if (newPassword == null || newPassword.length() < 6) {
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "message", "Şifre çok kısa! En az 6 karakter gereklidir."
                ));
            }

            Map<String, Object> result = personRepository.resetPassword(usernameOrEmail, code, newPassword);
            boolean isSuccess = Boolean.TRUE.equals(result.get("Result"));

            return ResponseEntity.ok(Map.of(
                    "success", isSuccess,
                    "message", result.getOrDefault("Message", isSuccess ? "Şifre başarıyla değiştirildi." : "İşlem başarısız.")
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", e.getMessage()
            ));
        }
    }
}
