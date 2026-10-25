package com.ses.bldizi.service;

import com.ses.bldizi.config.WebPushConfig;
import com.ses.bldizi.exception.PushPermanentException;
import com.ses.bldizi.exception.PushSubscriptionExpiredException;
import com.ses.bldizi.exception.PushTemporaryException;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;
import com.ses.bldizi.model.PushSubscription;
import org.apache.http.HttpResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.List;
import java.util.Locale;

@Service
public class WebPushClientService {

    private static final Logger logger = LoggerFactory.getLogger(WebPushClientService.class);
    private final WebPushConfig config;

    private static final List<String> TRUSTED_PUSH_DOMAINS = List.of(
            "push.services.mozilla.com",
            "notify.windows.com",
            "push.apple.com",
            "web.push.apple.com",
            "googleapis.com",
            "google.com"
    );

    public WebPushClientService(WebPushConfig config) {
        this.config = config;
    }

    /**
     * Validates push endpoint against SSRF and untrusted host attacks.
     */
    public void validateEndpoint(String endpoint) {
        if (endpoint == null || endpoint.isBlank()) {
            throw new IllegalArgumentException("Push endpoint boş olamaz.");
        }

        URI uri;
        try {
            uri = URI.create(endpoint.trim());
        } catch (Exception e) {
            throw new IllegalArgumentException("Geçersiz push endpoint URI formatı.");
        }

        if (!"https".equalsIgnoreCase(uri.getScheme())) {
            throw new SecurityException("Push endpoint yalnızca güvenli HTTPS protokolü kullanmalıdır.");
        }

        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new SecurityException("Push endpoint host bilgisi eksik.");
        }

        String lowerHost = host.toLowerCase(Locale.ROOT);

        // SSRF Check: DNS resolution must not point to private, loopback, or local IP ranges
        try {
            InetAddress[] addresses = InetAddress.getAllByName(lowerHost);
            for (InetAddress addr : addresses) {
                if (addr.isLoopbackAddress() || addr.isAnyLocalAddress() || addr.isLinkLocalAddress()
                        || addr.isSiteLocalAddress() || addr.isMulticastAddress()) {
                    throw new SecurityException("Push endpoint özel/yerel ağ adresine çözümlenemez.");
                }

                byte[] bytes = addr.getAddress();
                if (bytes.length == 4) {
                    int b0 = bytes[0] & 0xFF;
                    int b1 = bytes[1] & 0xFF;
                    if (b0 == 10 || b0 == 127 || (b0 == 172 && b1 >= 16 && b1 <= 31) || (b0 == 192 && b1 == 168) || (b0 == 169 && b1 == 254)) {
                        throw new SecurityException("Push endpoint yasaklı IP aralığına yönlendirilemez.");
                    }
                }
            }
        } catch (SecurityException se) {
            throw se;
        } catch (Exception e) {
            throw new SecurityException("Push endpoint host doğrulaması başarısız: " + e.getMessage());
        }

        // Domain Suffix Check: must belong to a known legitimate Web Push service
        boolean isTrusted = false;
        for (String trustedDomain : TRUSTED_PUSH_DOMAINS) {
            if (lowerHost.equals(trustedDomain) || lowerHost.endsWith("." + trustedDomain)) {
                isTrusted = true;
                break;
            }
        }

        if (!isTrusted) {
            throw new SecurityException("Push endpoint güvenilir bir web push sağlayıcısına ait değil.");
        }
    }

    /**
     * Sends encrypted Web Push message to the given subscription endpoint.
     * Note: Subscription endpoint and auth keys are NEVER logged.
     */
    
    public void sendPush(PushSubscription subscription, String payloadJson) {
        if (subscription == null) return;
        sendPush(subscription.getEndpoint(), subscription.getP256dh(), subscription.getAuth(), payloadJson, 86400);
    }

    public void sendPush(String endpoint, String p256dh, String auth, String payloadJson, int ttlSeconds) {
        if (!config.isEnabled()) {
            logger.debug("Web Push is currently disabled or VAPID keys are missing. Skipping notification dispatch.");
            return;
        }

        validateEndpoint(endpoint);

        try {
            PushService pushService = new PushService(
                    config.getPublicKey(),
                    config.getPrivateKey(),
                    config.getSubject()
            );

            byte[] payloadBytes = payloadJson != null ? payloadJson.getBytes(StandardCharsets.UTF_8) : new byte[0];

            Notification notification = new Notification(
                    endpoint,
                    p256dh,
                    auth,
                    payloadBytes,
                    Math.max(60, ttlSeconds)
            );

            HttpResponse response = pushService.send(notification);
            int statusCode = response.getStatusLine().getStatusCode();

            if (statusCode == 200 || statusCode == 201 || statusCode == 202) {
                logger.debug("Push notification accepted by gateway (status {}).", statusCode);
                return;
            }

            if (statusCode == 404 || statusCode == 410) {
                throw new PushSubscriptionExpiredException("Push aboneliği süresi dolmuş veya kaldırılmış (HTTP " + statusCode + ").");
            }

            if (statusCode == 429 || statusCode >= 500) {
                throw new PushTemporaryException("Push sağlayıcısı geçici olarak yanıt vermedi (HTTP " + statusCode + ").", statusCode);
            }

            // 400, 401, 403, etc.
            throw new PushPermanentException("Push gönderimi kalıcı olarak başarısız oldu (HTTP " + statusCode + ").", statusCode);

        } catch (PushSubscriptionExpiredException | PushTemporaryException | PushPermanentException e) {
            throw e;
        } catch (GeneralSecurityException e) {
            throw new PushPermanentException("Kriptografik hata veya geçersiz VAPID anahtarı: " + e.getMessage(), 401);
        } catch (Exception e) {
            throw new PushTemporaryException("Ağ bağlantısı veya iletişim hatası: " + e.getMessage(), 500);
        }
    }
}
