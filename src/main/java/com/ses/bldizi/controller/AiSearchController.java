package com.ses.bldizi.controller;

import com.ses.bldizi.model.AiSearchRecommendation;
import com.ses.bldizi.service.GeminiAiSearchService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api/search/ai")
public class AiSearchController {

    private static final Logger logger = LoggerFactory.getLogger(AiSearchController.class);

    // 1 minute (60 seconds) cooldown between prompts
    private static final long COOLDOWN_MS = 60 * 1000L;

    private final GeminiAiSearchService geminiAiSearchService;
    private final ConcurrentHashMap<String, Long> lastPromptTimestamps = new ConcurrentHashMap<>();

    public AiSearchController(GeminiAiSearchService geminiAiSearchService) {
        this.geminiAiSearchService = geminiAiSearchService;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> searchByPromptGet(
            @RequestParam(name = "prompt", required = false) String prompt,
            HttpServletRequest request) {
        return processAiSearch(prompt, request);
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> searchByPromptPost(
            @RequestBody(required = false) Map<String, String> body,
            HttpServletRequest request) {
        String prompt = body != null ? body.get("prompt") : null;
        return processAiSearch(prompt, request);
    }

    @PostMapping("/refresh-cache")
    public ResponseEntity<Map<String, Object>> refreshCache() {
        geminiAiSearchService.invalidateCache();
        int count = geminiAiSearchService.getCachedCatalog().size();
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "AI katalog önbelleği başarıyla yenilendi.",
                "totalItems", count
        ));
    }

    private ResponseEntity<Map<String, Object>> processAiSearch(String prompt, HttpServletRequest request) {
        Map<String, Object> response = new HashMap<>();

        if (prompt == null || prompt.trim().isEmpty()) {
            response.put("success", false);
            response.put("message", "Lütfen aramak istediğiniz ruh halini veya hissiyatı (vibe) yazın.");
            return ResponseEntity.badRequest().body(response);
        }

        // Rate Limiting (1-minute cooldown per IP)
        String clientIp = extractClientIp(request);
        long now = System.currentTimeMillis();
        Long lastRequestTime = lastPromptTimestamps.get(clientIp);

        if (lastRequestTime != null && (now - lastRequestTime) < COOLDOWN_MS) {
            long remainingSeconds = ((COOLDOWN_MS - (now - lastRequestTime)) + 999) / 1000;
            logger.warn("Rate limit triggered for IP {}. Cooldown remaining: {} seconds", clientIp, remainingSeconds);
            response.put("success", false);
            response.put("rateLimited", true);
            response.put("remainingSeconds", remainingSeconds);
            response.put("message", "Yeni bir yapay zeka önerisi istemek için lütfen " + remainingSeconds + " saniye bekleyin.");
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(response);
        }

        // Maintenance: prune stale entries if map exceeds 5000 records
        if (lastPromptTimestamps.size() > 5000) {
            lastPromptTimestamps.entrySet().removeIf(entry -> (now - entry.getValue()) > 10 * 60 * 1000L);
        }

        try {
            logger.info("Executing AI Vibe Search for prompt: '{}' (Client IP: {})", prompt, clientIp);
            List<AiSearchRecommendation> recommendations = geminiAiSearchService.searchByVibe(prompt.trim());

            // Record cooldown timestamp after successful search
            lastPromptTimestamps.put(clientIp, now);

            response.put("success", true);
            response.put("prompt", prompt.trim());
            response.put("count", recommendations.size());
            response.put("results", recommendations);
            response.put("cooldownSeconds", 60);

            return ResponseEntity.ok(response);
        } catch (IllegalStateException e) {
            logger.warn("AI Search config warning: {}", e.getMessage());
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.status(400).body(response);
        } catch (Exception e) {
            logger.error("AI Search execution failed: {}", e.getMessage(), e);
            response.put("success", false);
            response.put("message", "Yapay zeka önerisi oluşturulurken bir hata oluştu: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    private String extractClientIp(HttpServletRequest request) {
        if (request == null) return "unknown";
        String ip = request.getHeader("X-Forwarded-For");
        if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
            int commaIndex = ip.indexOf(',');
            return commaIndex > 0 ? ip.substring(0, commaIndex).trim() : ip.trim();
        }
        ip = request.getHeader("X-Real-IP");
        if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
            return ip.trim();
        }
        return request.getRemoteAddr();
    }
}
