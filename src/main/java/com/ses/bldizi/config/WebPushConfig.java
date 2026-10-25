package com.ses.bldizi.config;

import jakarta.annotation.PostConstruct;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import java.security.Security;

@Configuration
public class WebPushConfig {

    private static final Logger logger = LoggerFactory.getLogger(WebPushConfig.class);

    @Value("${webpush.vapid.public-key:}")
    private String publicKey;

    @Value("${webpush.vapid.private-key:}")
    private String privateKey;

    @Value("${webpush.vapid.subject:mailto:info@bldizi.com}")
    private String subject;

    @Value("${webpush.enabled:true}")
    private boolean enabled;

    @PostConstruct
    public void init() {
        if (Security.getProvider("BC") == null) {
            Security.addProvider(new BouncyCastleProvider());
            logger.info("BouncyCastle security provider registered for Web Push.");
        }

        if (publicKey == null || publicKey.isBlank() || privateKey == null || privateKey.isBlank()) {
            logger.warn("Web Push VAPID anahtarları yapılandırılmamış. Lütfen ortam değişkenleri (VAPID_PUBLIC_KEY, VAPID_PRIVATE_KEY) ile kalıcı anahtarları belirleyin.");
        } else {
            logger.info("Web Push VAPID yapılandırması başarıyla yüklendi (Subject: {}).", subject);
        }
    }

    public String getPublicKey() {
        return publicKey != null ? publicKey.trim() : "";
    }

    public String getPrivateKey() {
        return privateKey != null ? privateKey.trim() : "";
    }

    public String getSubject() {
        return subject != null ? subject.trim() : "mailto:info@bldizi.com";
    }

    public boolean isEnabled() {
        return enabled && !getPublicKey().isEmpty() && !getPrivateKey().isEmpty();
    }
}
