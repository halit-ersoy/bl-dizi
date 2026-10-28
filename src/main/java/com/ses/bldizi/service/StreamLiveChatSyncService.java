package com.ses.bldizi.service;

import com.ses.bldizi.model.StreamLive;
import com.ses.bldizi.model.StreamLiveChat;
import com.ses.bldizi.repository.StreamLiveRepository;
import com.ses.bldizi.model.StreamLiveChatMessageDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.net.ssl.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

@Service
public class StreamLiveChatSyncService {

    private static final Logger logger = LoggerFactory.getLogger(StreamLiveChatSyncService.class);

    private final StreamLiveRepository streamLiveRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    @Value("${streamlive.peer.urls:}")
    private String peerUrls;

    // Cache of recently broadcasted message IDs to prevent duplicates and loops
    private final Set<UUID> knownBroadcastMessageIds = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Queue<UUID> evictionQueue = new ConcurrentLinkedQueue<>();
    private static final int MAX_KNOWN_CACHE_SIZE = 1000;

    public StreamLiveChatSyncService(StreamLiveRepository streamLiveRepository,
                                     SimpMessagingTemplate messagingTemplate,
                                     ObjectMapper objectMapper) {
        this.streamLiveRepository = streamLiveRepository;
        this.messagingTemplate = messagingTemplate;
        this.objectMapper = objectMapper;
        this.httpClient = createTrustAllHttpClient();
    }

    /**
     * Records a message as broadcasted on this instance.
     * Returns true if newly added, false if already known.
     */
    public boolean recordBroadcast(UUID messageId) {
        if (messageId == null) return false;
        boolean added = knownBroadcastMessageIds.add(messageId);
        if (added) {
            evictionQueue.add(messageId);
            while (evictionQueue.size() > MAX_KNOWN_CACHE_SIZE) {
                UUID oldest = evictionQueue.poll();
                if (oldest != null) {
                    knownBroadcastMessageIds.remove(oldest);
                }
            }
        }
        return added;
    }

    /**
     * Broadcasts a message to local WebSocket clients if not already broadcasted.
     */
    public void broadcastLocally(UUID streamLiveId, StreamLiveChatMessageDto dto) {
        if (dto == null || dto.getId() == null) return;
        if (recordBroadcast(dto.getId())) {
            messagingTemplate.convertAndSend("/topic/streamlive/" + streamLiveId + "/chat", dto);
        }
    }

    /**
     * Relays a freshly posted message to configured peer instances asynchronously (non-blocking).
     */
    public void relayToPeersAsync(UUID streamLiveId, StreamLiveChatMessageDto dto) {
        if (peerUrls == null || peerUrls.isBlank() || dto == null) return;

        // Record locally first so we never re-broadcast our own message
        recordBroadcast(dto.getId());

        String[] urls = peerUrls.split(",");
        for (String rawUrl : urls) {
            String baseUrl = rawUrl.trim();
            if (baseUrl.isEmpty()) continue;

            CompletableFuture.runAsync(() -> {
                try {
                    String targetUrl = baseUrl.replaceAll("/+$", "") + "/api/streamlive/internal/" + streamLiveId + "/relay-chat";
                    String json = objectMapper.writeValueAsString(dto);

                    HttpRequest request = HttpRequest.newBuilder()
                            .uri(URI.create(targetUrl))
                            .timeout(Duration.ofMillis(1500))
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(json))
                            .build();

                    httpClient.sendAsync(request, HttpResponse.BodyHandlers.discarding())
                            .exceptionally(ex -> {
                                logger.trace("Failed to relay chat to {}: {}", targetUrl, ex.getMessage());
                                return null;
                            });
                } catch (Exception e) {
                    logger.trace("Error preparing chat relay to {}: {}", baseUrl, e.getMessage());
                }
            });
        }
    }

    /**
     * Fallback DB poller: Runs every 1000ms.
     * Checks if there are any new messages in the database for the active stream that were not broadcasted locally.
     */
    @Scheduled(fixedDelay = 1000)
    public void syncNewMessagesFromDatabase() {
        try {
            StreamLive activeStream = streamLiveRepository.findActiveOrUpcomingStream("ALL");
            if (activeStream == null) return;

            UUID streamId = activeStream.getId();
            List<StreamLiveChat> recent = streamLiveRepository.getRecentChatMessages(streamId, 30);
            if (recent == null || recent.isEmpty()) return;

            for (StreamLiveChat c : recent) {
                if (!knownBroadcastMessageIds.contains(c.getId())) {
                    StreamLiveChatMessageDto dto = new StreamLiveChatMessageDto(
                            c.getId(),
                            c.getStreamLiveId(),
                            c.getUserId(),
                            c.getNickname(),
                            c.getMessage(),
                            c.getSentAt().getTime(),
                            c.isVerified()
                    );
                    broadcastLocally(streamId, dto);
                }
            }
        } catch (Exception e) {
            logger.trace("Chat DB sync ticker error: {}", e.getMessage());
        }
    }

    private static HttpClient createTrustAllHttpClient() {
        try {
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, new TrustManager[]{new X509TrustManager() {
                public void checkClientTrusted(X509Certificate[] chain, String authType) {}
                public void checkServerTrusted(X509Certificate[] chain, String authType) {}
                public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
            }}, new SecureRandom());

            return HttpClient.newBuilder()
                    .sslContext(sslContext)
                    .connectTimeout(Duration.ofMillis(1200))
                    .build();
        } catch (Exception e) {
            return HttpClient.newBuilder()
                    .connectTimeout(Duration.ofMillis(1200))
                    .build();
        }
    }
}