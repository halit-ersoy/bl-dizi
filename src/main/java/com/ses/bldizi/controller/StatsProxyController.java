package com.ses.bldizi.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.net.ssl.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.Map;

@RestController
@RequestMapping("/api/stats")
public class StatsProxyController {

    @Value("${whodatidols.stats.url:https://localhost:8443/api/stats}")
    private String whodatStatsUrl;

    private final HttpClient httpClient;

    public StatsProxyController() {
        this.httpClient = createTrustAllHttpClient();
    }

    @PostMapping("/heartbeat")
    public ResponseEntity<?> forwardHeartbeat(HttpServletRequest request) {
        try {
            String clientIp = request.getHeader("X-Forwarded-For");
            if (clientIp == null || clientIp.isEmpty()) {
                clientIp = request.getRemoteAddr();
            } else if (clientIp.contains(",")) {
                clientIp = clientIp.split(",")[0].trim();
            }

            String userAgent = request.getHeader("User-Agent");
            if (userAgent == null) userAgent = "bldizi-visitor";

            HttpRequest forwardRequest = HttpRequest.newBuilder()
                    .uri(URI.create(whodatStatsUrl + "/heartbeat"))
                    .timeout(Duration.ofSeconds(3))
                    .header("Content-Type", "application/json")
                    .header("X-Forwarded-For", clientIp)
                    .header("User-Agent", userAgent)
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .build();

            httpClient.sendAsync(forwardRequest, HttpResponse.BodyHandlers.discarding());
        } catch (Exception e) {
            // Sessizce logla veya yut (kullanıcı deneyimini etkilemesin)
        }
        return ResponseEntity.ok().build();
    }

    @GetMapping("/active-users")
    public ResponseEntity<Map<String, Object>> getActiveUsers() {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(whodatStatsUrl + "/active-users"))
                    .timeout(Duration.ofSeconds(3))
                    .GET()
                    .build();

            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                return ResponseEntity.ok()
                        .header("Content-Type", "application/json")
                        .body(Map.of("raw", resp.body()));
            }
        } catch (Exception ignored) {
        }
        return ResponseEntity.ok(Map.of("activeUsers", 0));
    }

    private static HttpClient createTrustAllHttpClient() {
        try {
            TrustManager[] trustAllCerts = new TrustManager[]{
                    new X509TrustManager() {
                        public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                        public void checkClientTrusted(X509Certificate[] certs, String authType) {}
                        public void checkServerTrusted(X509Certificate[] certs, String authType) {}
                    }
            };

            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, trustAllCerts, new SecureRandom());

            return HttpClient.newBuilder()
                    .sslContext(sslContext)
                    .connectTimeout(Duration.ofSeconds(3))
                    .build();
        } catch (Exception e) {
            return HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(3))
                    .build();
        }
    }
}
