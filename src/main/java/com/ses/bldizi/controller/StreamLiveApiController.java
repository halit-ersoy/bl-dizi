package com.ses.bldizi.controller;

import com.ses.bldizi.model.StreamLiveChatMessageDto;
import com.ses.bldizi.model.StreamLiveStatusDto;
import com.ses.bldizi.repository.PersonRepository;
import com.ses.bldizi.service.StreamLiveChatSyncService;
import com.ses.bldizi.service.StreamLiveService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/streamlive")
public class StreamLiveApiController {

    private final StreamLiveService streamLiveService;
    private final StreamLiveChatSyncService streamLiveChatSyncService;
    private final PersonRepository personRepository;

    public StreamLiveApiController(StreamLiveService streamLiveService,
                                   StreamLiveChatSyncService streamLiveChatSyncService,
                                   PersonRepository personRepository) {
        this.streamLiveService = streamLiveService;
        this.streamLiveChatSyncService = streamLiveChatSyncService;
        this.personRepository = personRepository;
    }

    @GetMapping("/current")
    public ResponseEntity<StreamLiveStatusDto> getCurrentStream() {
        StreamLiveStatusDto dto = streamLiveService.getCurrentOrUpcomingStreamStatus();
        if (dto == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(dto);
    }

    @GetMapping("/schedule")
    public ResponseEntity<StreamLiveStatusDto> getSchedule() {
        StreamLiveStatusDto dto = streamLiveService.getScheduleOrLatestStreamStatus();
        if (dto == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(dto);
    }

    @GetMapping("/{id}")
    public ResponseEntity<StreamLiveStatusDto> getStreamById(@PathVariable("id") UUID id) {
        StreamLiveStatusDto dto = streamLiveService.getStreamStatusById(id);
        if (dto == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(dto);
    }

    @GetMapping("/{id}/viewers")
    public ResponseEntity<Map<String, Object>> getViewerCount(@PathVariable("id") UUID id) {
        int count = streamLiveService.getViewerCount(id);
        return ResponseEntity.ok(Map.of("viewerCount", count));
    }

    @GetMapping("/{id}/chat/history")
    public ResponseEntity<List<StreamLiveChatMessageDto>> getChatHistory(
            @PathVariable("id") UUID id,
            @RequestParam(value = "limit", defaultValue = "50") int limit) {
        List<StreamLiveChatMessageDto> history = streamLiveService.getRecentChatHistory(id, limit);
        return ResponseEntity.ok(history);
    }

    @PostMapping("/internal/{id}/relay-chat")
    public ResponseEntity<Void> receiveRelayedChat(
            @PathVariable("id") UUID streamLiveId,
            @RequestBody StreamLiveChatMessageDto dto) {
        streamLiveChatSyncService.broadcastLocally(streamLiveId, dto);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/auth-status")
    public ResponseEntity<Map<String, Object>> getAuthStatus(
            @CookieValue(name = "wdiAuth", required = false) String cookie) {
        Map<String, Object> response = new HashMap<>();
        if (cookie != null && !cookie.isBlank()) {
            try {
                Map<String, Object> userInfo = personRepository.getUserInfoByCookie(cookie.trim());
                if (userInfo != null && userInfo.containsKey("nickname")) {
                    response.put("isLoggedIn", true);
                    response.put("userId", userInfo.get("ID"));
                    response.put("nickname", userInfo.get("nickname"));
                    response.put("role", userInfo.get("role"));
                    response.put("isVerified", userInfo.get("isVerified"));
                    return ResponseEntity.ok(response);
                }
            } catch (Exception ignored) {
            }
        }
        response.put("isLoggedIn", false);
        return ResponseEntity.ok(response);
    }
}