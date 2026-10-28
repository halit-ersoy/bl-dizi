package com.ses.bldizi.service;

import com.ses.bldizi.model.StreamLive;
import com.ses.bldizi.model.StreamLiveChat;
import com.ses.bldizi.model.StreamLiveChatMessageDto;
import com.ses.bldizi.model.StreamLiveStatusDto;
import com.ses.bldizi.repository.StreamLiveRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class StreamLiveService {

    private static final Logger logger = LoggerFactory.getLogger(StreamLiveService.class);
    private static final long ONE_HOUR_MILLIS = 60 * 60 * 1000L;
    private static final long CHAT_COOLDOWN_MILLIS = 10000L;
    private static final int MAX_MESSAGE_LENGTH = 200;

    private final StreamLiveRepository streamLiveRepository;
    private final StreamLiveViewerTracker streamLiveViewerTracker;

    private final Map<UUID, Long> userLastMessageTimes = new ConcurrentHashMap<>();

    public StreamLiveService(StreamLiveRepository streamLiveRepository,
                             StreamLiveViewerTracker streamLiveViewerTracker) {
        this.streamLiveRepository = streamLiveRepository;
        this.streamLiveViewerTracker = streamLiveViewerTracker;
    }

    public int getViewerCount(UUID streamId) {
        return streamLiveViewerTracker != null ? streamLiveViewerTracker.getViewerCount(streamId) : 0;
    }

    public StreamLiveStatusDto getCurrentOrUpcomingStreamStatus() {
        StreamLive stream = streamLiveRepository.findActiveOrUpcomingStream();
        if (stream == null) {
            return null;
        }
        return toStatusDto(stream);
    }

    public StreamLiveStatusDto getScheduleOrLatestStreamStatus() {
        StreamLive stream = streamLiveRepository.findScheduleOrLatestStream();
        if (stream == null) {
            return null;
        }
        StreamLiveStatusDto dto = toStatusDto(stream);
        if (dto == null || (!"LIVE".equals(dto.getStatus()) && !"UPCOMING".equals(dto.getStatus()))) {
            return null;
        }
        return dto;
    }

    public StreamLiveStatusDto getStreamStatusById(UUID id) {
        StreamLive stream = streamLiveRepository.findStreamById(id);
        if (stream == null) {
            return null;
        }
        return toStatusDto(stream);
    }

    public StreamLiveStatusDto toStatusDto(StreamLive stream) {
        long now = System.currentTimeMillis();
        long start = stream.getScheduledStartTime().getTime();
        long end = stream.getEndTime().getTime();
        long totalSeconds = stream.getDurationSeconds() > 0
                ? stream.getDurationSeconds()
                : Math.max(0, (long) stream.getDurationMinutes() * 60L);

        String status;
        long liveEdgeSeconds;
        long remainingStartSeconds = 0;

        if (now < start) {
            status = "UPCOMING";
            liveEdgeSeconds = 0;
            remainingStartSeconds = (start - now) / 1000L;
        } else if (now <= end) {
            status = "LIVE";
            liveEdgeSeconds = Math.min(totalSeconds, (now - start) / 1000L);
        } else if (now <= end + ONE_HOUR_MILLIS) {
            status = "ENDED";
            liveEdgeSeconds = totalSeconds;
        } else {
            status = "CLOSED";
            liveEdgeSeconds = totalSeconds;
        }

        long tenMinutesBeforeStart = start - (10L * 60L * 1000L);
        boolean chatOpen;
        long chatOpenInSeconds = 0;
        if (now < tenMinutesBeforeStart) {
            chatOpen = false;
            chatOpenInSeconds = (tenMinutesBeforeStart - now) / 1000L;
        } else if (now <= end + ONE_HOUR_MILLIS) {
            chatOpen = true;
        } else {
            chatOpen = false;
        }

        StreamLiveStatusDto dto = new StreamLiveStatusDto(
                stream.getId(),
                stream.getTitle(),
                stream.getDescription(),
                stream.getContentType(),
                stream.getContentId(),
                stream.getContentName(),
                stream.getSlug(),
                stream.getPosterUrl(),
                stream.getVideoUrl(),
                start,
                stream.getDurationMinutes(),
                (int) totalSeconds,
                end,
                now,
                liveEdgeSeconds,
                remainingStartSeconds,
                status,
                stream.isActive(),
                chatOpen,
                chatOpenInSeconds
        );

        if (streamLiveViewerTracker != null) {
            dto.setViewerCount(streamLiveViewerTracker.getViewerCount(stream.getId()));
        }
        dto.setPlatform(stream.getPlatform());

        return dto;
    }

    public StreamLiveChatMessageDto processChatMessage(UUID streamLiveId, UUID userId, String nickname, String messageText) {
        if (messageText == null) {
            throw new IllegalArgumentException("Mesaj boş olamaz.");
        }
        String trimmed = messageText.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("Mesaj boş olamaz.");
        }
        if (trimmed.length() > MAX_MESSAGE_LENGTH) {
            throw new IllegalArgumentException("Mesaj en fazla " + MAX_MESSAGE_LENGTH + " karakter olabilir.");
        }

        long now = System.currentTimeMillis();
        Long lastTime = userLastMessageTimes.get(userId);
        if (lastTime != null && (now - lastTime) < CHAT_COOLDOWN_MILLIS) {
            long remainingSec = ((CHAT_COOLDOWN_MILLIS - (now - lastTime)) / 1000) + 1;
            throw new IllegalStateException("Lütfen yeni mesaj göndermek için " + remainingSec + " saniye bekleyin.");
        }

        StreamLive stream = streamLiveRepository.findStreamById(streamLiveId);
        if (stream == null || !stream.isActive()) {
            throw new IllegalStateException("Yayın bulunamadı veya aktif değil.");
        }
        long start = stream.getScheduledStartTime().getTime();
        long tenMinutesBeforeStart = start - (10L * 60L * 1000L);
        if (now < tenMinutesBeforeStart) {
            throw new IllegalStateException("Canlı sohbet yayının başlamasına 10 dakika kala açılacaktır.");
        }
        long end = stream.getEndTime().getTime();
        if (now > end + ONE_HOUR_MILLIS) {
            throw new IllegalStateException("Yayın ve sohbet süresi sona erdi.");
        }

        boolean isVerified = streamLiveRepository.isUserVerified(userId);
        userLastMessageTimes.put(userId, now);

        StreamLiveChat chat = new StreamLiveChat();
        chat.setId(UUID.randomUUID());
        chat.setStreamLiveId(streamLiveId);
        chat.setUserId(userId);
        chat.setNickname(nickname != null ? nickname : "Kullanıcı");
        chat.setMessage(trimmed);
        chat.setSentAt(new Timestamp(now));
        chat.setVerified(isVerified);

        streamLiveRepository.saveChatMessage(chat);

        return new StreamLiveChatMessageDto(
                chat.getId(),
                chat.getStreamLiveId(),
                chat.getUserId(),
                chat.getNickname(),
                chat.getMessage(),
                now,
                isVerified
        );
    }

    public List<StreamLiveChatMessageDto> getRecentChatHistory(UUID streamLiveId, int limit) {
        return streamLiveRepository.getRecentChatMessages(streamLiveId, Math.min(limit, 100))
                .stream()
                .map(c -> new StreamLiveChatMessageDto(
                        c.getId(),
                        c.getStreamLiveId(),
                        c.getUserId(),
                        c.getNickname(),
                        c.getMessage(),
                        c.getSentAt().getTime(),
                        c.isVerified()
                ))
                .collect(Collectors.toList());
    }
}
