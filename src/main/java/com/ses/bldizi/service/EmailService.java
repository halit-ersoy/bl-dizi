package com.ses.bldizi.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    private static final Logger logger = LoggerFactory.getLogger(EmailService.class);
    private final JavaMailSender mailSender;

    @Autowired(required = false)
    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public void sendVerificationCode(String email, String code) {
        if (mailSender == null) {
            logger.warn("JavaMailSender is not configured. Verification code for {}: {}", email, code);
            return;
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom("info@bldizi.com");
            helper.setTo(email);
            helper.setSubject("BL Dizi - Hesap Doğrulama Kodu");

            String htmlContent = buildVerificationEmailTemplate(code);
            helper.setText(htmlContent, true);

            mailSender.send(message);
        } catch (MessagingException e) {
            logger.error("Failed to send verification email to {}: {}", email, e.getMessage());
            throw new RuntimeException("Doğrulama kodu gönderilirken hata oluştu: " + e.getMessage());
        }
    }

    private String buildVerificationEmailTemplate(String code) {
        return "<div style='font-family:Arial,sans-serif;max-width:600px;margin:0 auto;padding:20px;background:#18181c;color:#fff;border-radius:10px;border:1px solid #ff4081;'>"
                + "<div style='text-align:center;margin-bottom:30px;'>"
                + "<h1 style='color:#ff4081;margin:0;letter-spacing:2px;'>BL DİZİ</h1>"
                + "<p style='color:#ccc;font-size:16px;margin-top:10px;'>Hesap Doğrulama</p>"
                + "</div>"
                + "<p>Merhaba,</p>"
                + "<p>Hesabınızı doğrulamak veya şifrenizi sıfırlamak için aşağıdaki doğrulama kodunu kullanın:</p>"
                + "<div style='text-align:center;margin:30px 0;'>"
                + "<div style='display:inline-block;background:#ff4081;padding:15px 30px;border-radius:6px;letter-spacing:6px;font-size:28px;font-weight:bold;color:#ffffff;'>"
                + code
                + "</div>"
                + "</div>"
                + "<p>Bu kod güvenlik nedeniyle 15 dakika içinde geçerliliğini yitirecektir.</p>"
                + "<p>Eğer bu talebi siz gerçekleştirmediyseniz, hesabınızın güvenliği için bu e-postayı dikkate almayınız.</p>"
                + "<hr style='border:none;border-top:1px solid #333;margin:30px 0;'>"
                + "<p style='color:#999;font-size:14px;text-align:center;'>&copy; BL Dizi. Tüm hakları saklıdır.</p>"
                + "</div>";
    }
}
