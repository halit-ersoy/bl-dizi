package com.ses.bldizi.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ses.bldizi.model.PushSubscriptionModel;
import com.ses.bldizi.repository.PushSubscriptionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.*;
import java.time.Duration;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Service
public class WebPushService {

    private static final Logger logger = LoggerFactory.getLogger(WebPushService.class);

    @Value("${webpush.vapid.public-key:}")
    private String vapidPublicKey;

    @Value("${webpush.vapid.private-key:}")
    private String vapidPrivateKey;

    @Value("${webpush.vapid.subject:mailto:admin@bldizi.com}")
    private String vapidSubject;

    private final PushSubscriptionRepository repository;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public WebPushService(PushSubscriptionRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_2)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public String getVapidPublicKey() {
        return vapidPublicKey != null ? vapidPublicKey.trim() : "";
    }

    public String getVapidPrivateKey() {
        return vapidPrivateKey != null ? vapidPrivateKey.trim() : "";
    }

    public String getVapidSubject() {
        return (vapidSubject != null && !vapidSubject.isBlank()) ? vapidSubject.trim() : "mailto:admin@bldizi.com";
    }

    public void sendPushAsync(String userNickname, String title, String body, String url, String tag) {
        CompletableFuture.runAsync(() -> {
            try {
                sendPushSync(userNickname, title, body, url, tag);
            } catch (Exception e) {
                logger.error("Background Web Push execution failed for {}: {}", userNickname, e.getMessage());
            }
        });
    }

    private void sendPushSync(String userNickname, String title, String body, String url, String tag) {
        if (userNickname == null) return;

        String privKey = getVapidPrivateKey();
        String pubKey = getVapidPublicKey();
        if (privKey.isEmpty() || pubKey.isEmpty()) {
            logger.warn("BLDizi Web Push VAPID keys not configured in environment, push skipped for user: {}", userNickname);
            return;
        }

        List<PushSubscriptionModel> subscriptions = repository.findByNickname(userNickname);
        if (subscriptions.isEmpty()) return;

        Map<String, Object> payloadMap = new HashMap<>();
        payloadMap.put("title", title != null ? title : "BLDizi");
        payloadMap.put("body", body != null ? body : "");
        payloadMap.put("url", url != null ? url : "/");
        payloadMap.put("tag", tag != null ? tag : ("bldizi_" + System.currentTimeMillis()));
        payloadMap.put("icon", "/elements/img/bldizi-logo-notif.png");
        payloadMap.put("badge", "/elements/img/bldizi-logo-notif.png");

        byte[] payloadBytes;
        try {
            payloadBytes = objectMapper.writeValueAsBytes(payloadMap);
        } catch (Exception e) {
            logger.error("Failed to serialize push payload: {}", e.getMessage());
            return;
        }

        for (PushSubscriptionModel sub : subscriptions) {
            try {
                URI endpointUri = URI.create(sub.getEndpoint());
                String origin = endpointUri.getScheme() + "://" + endpointUri.getHost()
                        + (endpointUri.getPort() != -1 ? ":" + endpointUri.getPort() : "");

                byte[] encryptedPayload = encryptPayload(sub.getP256dh(), sub.getAuth(), payloadBytes);
                String vapidAuth = createVapidAuthorizationHeader(origin, getVapidSubject(), pubKey, privKey);

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(endpointUri)
                        .timeout(Duration.ofSeconds(15))
                        .header("Content-Type", "application/octet-stream")
                        .header("Content-Encoding", "aes128gcm")
                        .header("TTL", "86400")
                        .header("Urgency", "normal")
                        .header("Authorization", vapidAuth)
                        .POST(HttpRequest.BodyPublishers.ofByteArray(encryptedPayload))
                        .build();

                HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
                int statusCode = response.statusCode();

                if (statusCode == 200 || statusCode == 201 || statusCode == 202) {
                    logger.debug("Web Push sent successfully to {} ({})", userNickname, statusCode);
                } else if (statusCode == 404 || statusCode == 410) {
                    logger.info("Subscription expired or unregistered ({}). Removing from DB: {}", statusCode, sub.getEndpoint());
                    repository.deleteByEndpoint(sub.getEndpoint());
                } else {
                    logger.warn("Web Push sent to {} returned unexpected status: {}", userNickname, statusCode);
                }
            } catch (Exception e) {
                logger.warn("Failed to deliver Web Push notification to {}: {}", userNickname, e.getMessage());
                if (e.getMessage() != null && (e.getMessage().contains("404") || e.getMessage().contains("410"))) {
                    repository.deleteByEndpoint(sub.getEndpoint());
                }
            }
        }
    }

    /**
     * RFC 8291 aes128gcm payload encryption
     */
    public static byte[] encryptPayload(String p256dhB64, String authB64, byte[] plaintext) throws Exception {
        byte[] userPublicKeyBytes = Base64.getUrlDecoder().decode(p256dhB64);
        byte[] userAuthBytes = Base64.getUrlDecoder().decode(authB64);

        // 1. Generate local ephemeral EC key pair
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC");
        kpg.initialize(new ECGenParameterSpec("secp256r1"));
        KeyPair ephemeralKp = kpg.generateKeyPair();
        ECPublicKey ephemeralPub = (ECPublicKey) ephemeralKp.getPublic();
        ECPrivateKey ephemeralPriv = (ECPrivateKey) ephemeralKp.getPrivate();
        byte[] asnpBytes = toUncompressed(ephemeralPub);

        // 2. ECDH Shared Secret
        KeyFactory kf = KeyFactory.getInstance("EC");
        ECPublicKey userPublicKey = fromUncompressed(userPublicKeyBytes, kf);

        KeyAgreement ka = KeyAgreement.getInstance("ECDH");
        ka.init(ephemeralPriv);
        ka.doPhase(userPublicKey, true);
        byte[] ecdhSecret = ka.generateSecret();

        // 3. HKDF step 1: IKM (RFC 8291 section 3.2)
        byte[] prkKey = hkdfExtract(userAuthBytes, ecdhSecret);

        ByteArrayOutputStream keyInfoStream = new ByteArrayOutputStream();
        keyInfoStream.write("WebPush: info\0".getBytes(StandardCharsets.UTF_8));
        keyInfoStream.write(userPublicKeyBytes);
        keyInfoStream.write(asnpBytes);
        byte[] keyInfo = keyInfoStream.toByteArray();

        byte[] ikm = hkdfExpand(prkKey, keyInfo, 32);

        // 4. HKDF step 2: CEK and Nonce (RFC 8291 section 3.3)
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);

        byte[] prk = hkdfExtract(salt, ikm);

        byte[] cekInfo = "Content-Encoding: aes128gcm\0".getBytes(StandardCharsets.UTF_8);
        byte[] cek = hkdfExpand(prk, cekInfo, 16);

        byte[] nonceInfo = "Content-Encoding: nonce\0".getBytes(StandardCharsets.UTF_8);
        byte[] nonce = hkdfExpand(prk, nonceInfo, 12);

        // 5. Encrypt with AES-GCM (record delimiter 0x02)
        byte[] record = new byte[plaintext.length + 1];
        System.arraycopy(plaintext, 0, record, 0, plaintext.length);
        record[plaintext.length] = 0x02;

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(cek, "AES"), new GCMParameterSpec(128, nonce));
        byte[] ciphertext = cipher.doFinal(record);

        // 6. Assemble body: salt(16) + rs(4) + idlen(1) + keyid(65) + ciphertext
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(salt);
        out.write(ByteBuffer.allocate(4).putInt(4096).array());
        out.write(65);
        out.write(asnpBytes);
        out.write(ciphertext);

        return out.toByteArray();
    }

    /**
     * RFC 8292 VAPID Authorization header creation
     */
    public static String createVapidAuthorizationHeader(String origin, String subject, String publicKeyB64, String privateKeyB64) throws Exception {
        String header = "{\"typ\":\"JWT\",\"alg\":\"ES256\"}";
        long now = System.currentTimeMillis() / 1000L;
        long exp = now + (12 * 3600); // 12 hours
        String claims = "{\"aud\":\"" + origin + "\",\"exp\":" + exp + ",\"sub\":\"" + subject + "\"}";

        String headerB64 = Base64.getUrlEncoder().withoutPadding().encodeToString(header.getBytes(StandardCharsets.UTF_8));
        String claimsB64 = Base64.getUrlEncoder().withoutPadding().encodeToString(claims.getBytes(StandardCharsets.UTF_8));
        String signingInput = headerB64 + "." + claimsB64;

        byte[] privBytes = Base64.getUrlDecoder().decode(privateKeyB64);
        KeyFactory kf = KeyFactory.getInstance("EC");
        ECPrivateKey privateKey = (ECPrivateKey) kf.generatePrivate(new PKCS8EncodedKeySpec(createPkcs8Key(privBytes)));

        Signature ecdsa = Signature.getInstance("SHA256withECDSA");
        ecdsa.initSign(privateKey);
        ecdsa.update(signingInput.getBytes(StandardCharsets.UTF_8));
        byte[] derSignature = ecdsa.sign();
        byte[] rawSignature = derToP1363(derSignature);

        String signatureB64 = Base64.getUrlEncoder().withoutPadding().encodeToString(rawSignature);
        String jwt = signingInput + "." + signatureB64;

        return "vapid t=" + jwt + ", k=" + publicKeyB64;
    }

    private static byte[] hkdfExtract(byte[] salt, byte[] ikm) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(salt != null && salt.length > 0 ? salt : new byte[32], "HmacSHA256"));
        return mac.doFinal(ikm);
    }

    private static byte[] hkdfExpand(byte[] prk, byte[] info, int length) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(prk, "HmacSHA256"));
        ByteArrayOutputStream okm = new ByteArrayOutputStream();
        byte[] t = new byte[0];
        byte counter = 1;
        while (okm.size() < length) {
            mac.update(t);
            mac.update(info);
            mac.update(counter);
            t = mac.doFinal();
            okm.write(t);
            counter++;
        }
        byte[] result = new byte[length];
        System.arraycopy(okm.toByteArray(), 0, result, 0, length);
        return result;
    }

    private static byte[] toUncompressed(ECPublicKey pub) {
        byte[] x = pub.getW().getAffineX().toByteArray();
        byte[] y = pub.getW().getAffineY().toByteArray();
        byte[] out = new byte[65];
        out[0] = 0x04;
        copyPadded(x, out, 1, 32);
        copyPadded(y, out, 33, 32);
        return out;
    }

    private static ECPublicKey fromUncompressed(byte[] bytes, KeyFactory kf) throws Exception {
        byte[] x = new byte[32];
        byte[] y = new byte[32];
        System.arraycopy(bytes, 1, x, 0, 32);
        System.arraycopy(bytes, 33, y, 0, 32);
        ECPoint point = new ECPoint(new java.math.BigInteger(1, x), new java.math.BigInteger(1, y));
        AlgorithmParameters params = AlgorithmParameters.getInstance("EC");
        params.init(new ECGenParameterSpec("secp256r1"));
        ECParameterSpec spec = params.getParameterSpec(ECParameterSpec.class);
        return (ECPublicKey) kf.generatePublic(new ECPublicKeySpec(point, spec));
    }

    private static byte[] createPkcs8Key(byte[] privBytes) {
        byte[] prefix = new byte[]{
                0x30, 0x41, 0x02, 0x01, 0x00, 0x30, 0x13, 0x06, 0x07, 0x2A, (byte) 0x86, 0x48,
                (byte) 0xCE, 0x3D, 0x02, 0x01, 0x06, 0x08, 0x2A, (byte) 0x86, 0x48, (byte) 0xCE,
                (byte) 0x3D, 0x03, 0x01, 0x07, 0x04, 0x27, 0x30, 0x25, 0x02, 0x01, 0x01, 0x04, 0x20
        };
        byte[] pkcs8 = new byte[prefix.length + 32];
        System.arraycopy(prefix, 0, pkcs8, 0, prefix.length);
        copyPadded(privBytes, pkcs8, prefix.length, 32);
        return pkcs8;
    }

    private static byte[] derToP1363(byte[] der) {
        ByteBuffer bb = ByteBuffer.wrap(der);
        if (bb.get() != 0x30) throw new IllegalArgumentException("Invalid DER signature");
        int len = bb.get() & 0xFF;
        if ((len & 0x80) != 0) {
            int lenBytes = len & 0x7F;
            while (lenBytes-- > 0) bb.get();
        }
        if (bb.get() != 0x02) throw new IllegalArgumentException("Expected INTEGER for r");
        int rLen = bb.get() & 0xFF;
        byte[] rBytes = new byte[rLen];
        bb.get(rBytes);

        if (bb.get() != 0x02) throw new IllegalArgumentException("Expected INTEGER for s");
        int sLen = bb.get() & 0xFF;
        byte[] sBytes = new byte[sLen];
        bb.get(sBytes);

        byte[] raw = new byte[64];
        copyPadded(rBytes, raw, 0, 32);
        copyPadded(sBytes, raw, 32, 32);
        return raw;
    }

    private static void copyPadded(byte[] src, byte[] dest, int destOffset, int length) {
        int srcOffset = 0;
        int srcLen = src.length;
        while (srcLen > length && src[srcOffset] == 0) {
            srcOffset++;
            srcLen--;
        }
        if (srcLen > length) {
            srcOffset += (srcLen - length);
            srcLen = length;
        }
        System.arraycopy(src, srcOffset, dest, destOffset + (length - srcLen), srcLen);
    }
}