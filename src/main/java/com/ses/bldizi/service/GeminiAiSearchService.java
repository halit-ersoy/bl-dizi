package com.ses.bldizi.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ses.bldizi.model.AiCatalogItem;
import com.ses.bldizi.model.AiSearchRecommendation;
import com.ses.bldizi.repository.AiCatalogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Service
public class GeminiAiSearchService {

    private static final Logger logger = LoggerFactory.getLogger(GeminiAiSearchService.class);

    @Value("${gemini.api.key:}")
    private String apiKey;

    @Value("${gemini.api.model:gemini-2.5-flash}")
    private String model;

    private final AiCatalogRepository catalogRepository;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    // In-memory catalog cache (refreshed every 30 minutes)
    private volatile List<AiCatalogItem> cachedCatalog = null;
    private volatile long lastCacheTime = 0;
    private static final long CACHE_DURATION_MS = 30 * 60 * 1000L;

    public GeminiAiSearchService(AiCatalogRepository catalogRepository) {
        this.catalogRepository = catalogRepository;
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
    }

    public synchronized List<AiCatalogItem> getCachedCatalog() {
        long now = System.currentTimeMillis();
        if (cachedCatalog == null || (now - lastCacheTime) > CACHE_DURATION_MS) {
            try {
                cachedCatalog = catalogRepository.findAllActiveCatalogItems();
                lastCacheTime = now;
                logger.info("AI BL Catalog cache refreshed with {} items.", cachedCatalog.size());
            } catch (Exception e) {
                logger.error("Failed to load catalog for AI search: {}", e.getMessage(), e);
                if (cachedCatalog == null) {
                    cachedCatalog = Collections.emptyList();
                }
            }
        }
        return cachedCatalog;
    }

    public void invalidateCache() {
        this.cachedCatalog = null;
        this.lastCacheTime = 0;
    }

    public List<AiSearchRecommendation> searchByVibe(String userPrompt) {
        if (userPrompt == null || userPrompt.trim().isEmpty()) {
            return Collections.emptyList();
        }

        // Check configured key or environment variable
        String effectiveApiKey = (apiKey != null && !apiKey.trim().isEmpty()) ? apiKey.trim() : System.getenv("GEMINI_API_KEY");
        if (effectiveApiKey == null || effectiveApiKey.trim().isEmpty() || effectiveApiKey.contains("BURAYA_GEMINI")) {
            throw new IllegalStateException("Gemini API anahtarı yapılandırılmamış. Lütfen 'GEMINI_API_KEY' ortam değişkenini veya 'gemini.api.key' ayarını tanımlayın.");
        }

        String effectiveModel = (model != null && !model.trim().isEmpty()) ? model.trim() : "gemini-2.5-flash";
        String envModel = System.getenv("GEMINI_API_MODEL");
        if (envModel != null && !envModel.trim().isEmpty()) {
            effectiveModel = envModel.trim();
        }

        List<AiCatalogItem> catalog = getCachedCatalog();
        if (catalog.isEmpty()) {
            logger.warn("AI BL Catalog is empty. Cannot perform vibe search.");
            return Collections.emptyList();
        }

        // Prepare compact catalog JSON for Gemini
        List<Map<String, Object>> compactCatalog = new ArrayList<>();
        Map<String, AiCatalogItem> itemMap = new HashMap<>();

        for (AiCatalogItem item : catalog) {
            if (item.getId() == null) continue;
            itemMap.put(item.getId(), item);

            Map<String, Object> map = new HashMap<>();
            map.put("id", item.getId());
            map.put("name", item.getName());
            map.put("type", item.getType());
            map.put("category", item.getCategory() != null ? item.getCategory() : "");
            if (item.getYear() != null) {
                map.put("year", item.getYear());
            }
            // Truncate summary if too long to save token cost while keeping essence
            String summary = item.getSummary();
            if (summary != null && summary.length() > 220) {
                summary = summary.substring(0, 220) + "...";
            }
            map.put("summary", summary != null ? summary : "");
            compactCatalog.add(map);
        }

        try {
            String catalogJson = objectMapper.writeValueAsString(compactCatalog);

            String systemPrompt = """
                Sen BL Dizi (bldizi.com) platformunun uzman yapay zeka asistanısın. Asya, Tayland, Kore, Japonya ve Tayvan BL (Boys' Love) dizi ve filmlerinde uzmansın.
                Kullanıcı sana nasıl bir ruh halinde olduğunu, aradığı hissi (vibe), modu veya izlemek istediği ilişki dinamiğini anlatacak.
                Sana sitemizdeki BL film ve dizi kataloğunu JSON olarak veriyorum.
                GÖREVİN:
                1. SADECE sana verilen katalogdaki yapımları kullan. Katalog dışından ASLA film/dizi önerme!
                2. Kullanıcının ruh haline, istediği atmosfere ve duygu durumuna EN İYİ uyan 3 ila 5 yapımı seç.
                3. Her yapım için 'matchReason' alanında kullanıcıya doğrudan ve samimi hitap eden, spoiler içermeyen, o yapımın neden bu mod için biçilmiş kaftan olduğunu anlatan 1-2 cümlelik tatlı bir açıklama yaz (Örnek: "Yaşadığın bu yorgun günün ardından kalbini sıcacık yapacak tatlı bir aşk hikayesiyle tüm stresini unutacaksın.").
                4. Yanıtını STRICTLY aşağıdaki JSON formatında dizi (array) olarak ver:
                [
                  {
                    "id": "item_id",
                    "name": "Yapım Adı",
                    "type": "Film veya Dizi",
                    "category": "Kategoriler",
                    "matchReason": "Neden tam aradığın hisse uyuyor?"
                  }
                ]
                """;

            String userContent = "Kullanıcının Aradığı Ruh Hali / Vibe:\n\"" + userPrompt + "\"\n\nBL Dizi Kataloğumuz:\n" + catalogJson;

            // Construct Gemini REST Payload
            Map<String, Object> systemInstructionPart = Map.of("text", systemPrompt);
            Map<String, Object> systemInstruction = Map.of("parts", List.of(systemInstructionPart));

            Map<String, Object> contentPart = Map.of("text", userContent);
            Map<String, Object> content = Map.of("parts", List.of(contentPart));

            Map<String, Object> generationConfig = new HashMap<>();
            generationConfig.put("temperature", 0.4);
            generationConfig.put("response_mime_type", "application/json");

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("system_instruction", systemInstruction);
            requestBody.put("contents", List.of(content));
            requestBody.put("generationConfig", generationConfig);

            String url = "https://generativelanguage.googleapis.com/v1beta/models/" + effectiveModel + ":generateContent?key=" + effectiveApiKey;

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            logger.info("Sending AI Vibe Search prompt to Gemini ({}) for query: '{}'", effectiveModel, userPrompt);
            String responseStr = restTemplate.postForObject(url, entity, String.class);

            JsonNode rootNode = objectMapper.readTree(responseStr);
            JsonNode candidatesNode = rootNode.path("candidates");

            if (candidatesNode.isArray() && !candidatesNode.isEmpty()) {
                JsonNode partsNode = candidatesNode.get(0).path("content").path("parts");
                if (partsNode.isArray() && !partsNode.isEmpty()) {
                    String jsonText = partsNode.get(0).path("text").asText();
                    List<Map<String, Object>> rawResults = objectMapper.readValue(jsonText, new TypeReference<>() {});

                    List<AiSearchRecommendation> recommendations = new ArrayList<>();
                    for (Map<String, Object> raw : rawResults) {
                        String id = (String) raw.get("id");
                        AiCatalogItem catalogItem = itemMap.get(id);

                        AiSearchRecommendation rec = new AiSearchRecommendation();
                        rec.setId(id);
                        rec.setName(catalogItem != null ? catalogItem.getName() : (String) raw.get("name"));
                        rec.setType(catalogItem != null ? catalogItem.getType() : (String) raw.get("type"));
                        rec.setCategory(catalogItem != null && catalogItem.getCategory() != null ? catalogItem.getCategory() : (String) raw.get("category"));
                        rec.setYear(catalogItem != null ? catalogItem.getYear() : null);
                        rec.setSlug(catalogItem != null ? catalogItem.getSlug() : null);
                        rec.setMatchReason((String) raw.get("matchReason"));

                        recommendations.add(rec);
                    }
                    return recommendations;
                }
            }

            return Collections.emptyList();
        } catch (Exception e) {
            logger.error("Error calling Gemini API for vibe search: {}", e.getMessage(), e);
            throw new RuntimeException("Gemini ile arama sırasında hata oluştu: " + e.getMessage(), e);
        }
    }
}
