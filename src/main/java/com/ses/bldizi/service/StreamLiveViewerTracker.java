package com.ses.bldizi.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;
import org.springframework.web.socket.messaging.SessionUnsubscribeEvent;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class StreamLiveViewerTracker {

    private static final Logger logger = LoggerFactory.getLogger(StreamLiveViewerTracker.class);
    private static final Pattern VIEWERS_TOPIC_PATTERN = Pattern.compile("^/topic/streamlive/([a-fA-F0-9\\-]+)/viewers$");

    private final SimpMessagingTemplate messagingTemplate;

    private final Map<UUID, Map<String, Set<String>>> streamViewers = new ConcurrentHashMap<>();
    private final Map<String, String> sessionViewerKeys = new ConcurrentHashMap<>();
    private final Map<String, Map<String, UUID>> sessionSubscriptions = new ConcurrentHashMap<>();

    public StreamLiveViewerTracker(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    private String resolveViewerKey(StompHeaderAccessor headerAccessor, String sessionId) {
        Map<String, Object> sessionAttributes = headerAccessor.getSessionAttributes();
        if (sessionAttributes != null && sessionAttributes.containsKey("userId")) {
            Object userId = sessionAttributes.get("userId");
            if (userId != null && !userId.toString().isBlank()) {
                return "user:" + userId.toString();
            }
        }
        return "session:" + sessionId;
    }

    public int getViewerCount(UUID streamId) {
        if (streamId == null) return 0;
        Map<String, Set<String>> viewers = streamViewers.get(streamId);
        return viewers != null ? viewers.size() : 0;
    }

    @EventListener
    public void handleSubscribe(SessionSubscribeEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        String destination = headerAccessor.getDestination();
        if (destination == null) return;

        Matcher matcher = VIEWERS_TOPIC_PATTERN.matcher(destination);
        if (matcher.matches()) {
            try {
                UUID streamId = UUID.fromString(matcher.group(1));
                String sessionId = headerAccessor.getSessionId();
                String subscriptionId = headerAccessor.getSubscriptionId();

                if (sessionId != null) {
                    String viewerKey = resolveViewerKey(headerAccessor, sessionId);
                    sessionViewerKeys.put(sessionId, viewerKey);

                    streamViewers.computeIfAbsent(streamId, k -> new ConcurrentHashMap<>())
                            .computeIfAbsent(viewerKey, k -> ConcurrentHashMap.newKeySet())
                            .add(sessionId);

                    if (subscriptionId != null) {
                        sessionSubscriptions.computeIfAbsent(sessionId, k -> new ConcurrentHashMap<>())
                                .put(subscriptionId, streamId);
                    }

                    broadcastViewerCount(streamId);
                }
            } catch (Exception e) {
                logger.warn("StreamLiveViewerTracker subscription parse error: {}", e.getMessage());
            }
        }
    }

    @EventListener
    public void handleUnsubscribe(SessionUnsubscribeEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = headerAccessor.getSessionId();
        String subscriptionId = headerAccessor.getSubscriptionId();

        if (sessionId != null && subscriptionId != null) {
            Map<String, UUID> subs = sessionSubscriptions.get(sessionId);
            if (subs != null) {
                UUID streamId = subs.remove(subscriptionId);
                if (streamId != null) {
                    boolean hasOtherSubs = subs.values().contains(streamId);
                    if (!hasOtherSubs) {
                        String viewerKey = sessionViewerKeys.getOrDefault(sessionId, "session:" + sessionId);
                        Map<String, Set<String>> streamMap = streamViewers.get(streamId);
                        if (streamMap != null) {
                            Set<String> sessions = streamMap.get(viewerKey);
                            if (sessions != null) {
                                sessions.remove(sessionId);
                                if (sessions.isEmpty()) {
                                    streamMap.remove(viewerKey);
                                }
                                broadcastViewerCount(streamId);
                            }
                        }
                    }
                }
                if (subs.isEmpty()) {
                    sessionSubscriptions.remove(sessionId);
                    sessionViewerKeys.remove(sessionId);
                }
            }
        }
    }

    @EventListener
    public void handleDisconnect(SessionDisconnectEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = headerAccessor.getSessionId();

        if (sessionId != null) {
            Map<String, UUID> subs = sessionSubscriptions.remove(sessionId);
            String viewerKey = sessionViewerKeys.remove(sessionId);
            if (viewerKey == null) {
                viewerKey = resolveViewerKey(headerAccessor, sessionId);
            }

            Set<UUID> affectedStreams = new HashSet<>();
            if (subs != null) {
                affectedStreams.addAll(subs.values());
            }

            for (Map.Entry<UUID, Map<String, Set<String>>> entry : streamViewers.entrySet()) {
                Map<String, Set<String>> streamMap = entry.getValue();
                Set<String> sessions = streamMap.get(viewerKey);
                if (sessions != null && sessions.remove(sessionId)) {
                    if (sessions.isEmpty()) {
                        streamMap.remove(viewerKey);
                    }
                    affectedStreams.add(entry.getKey());
                }
            }

            for (UUID streamId : affectedStreams) {
                broadcastViewerCount(streamId);
            }
        }
    }

    public void broadcastViewerCount(UUID streamId) {
        if (streamId == null) return;
        int count = getViewerCount(streamId);
        try {
            messagingTemplate.convertAndSend("/topic/streamlive/" + streamId + "/viewers", Map.of("viewerCount", count));
        } catch (Exception e) {
            logger.warn("StreamLive broadcastViewerCount error: {}", e.getMessage());
        }
    }
}
