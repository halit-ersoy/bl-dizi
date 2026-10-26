package com.ses.bldizi.service;

import com.ses.bldizi.model.CastMemberDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.*;

@Service
public class TmdbService {

    @Value("${tmdb.api.key:e6fa9265669faede5de7fd2f5f4a056b}")
    private String apiKey = "e6fa9265669faede5de7fd2f5f4a056b";

    private final String BASE_URL = "https://api.themoviedb.org/3";
    private final String IMAGE_BASE_URL = "https://image.tmdb.org/t/p/w500";
    private final String PROFILE_BASE_URL = "https://image.tmdb.org/t/p/w185";
    private final RestTemplate restTemplate = new RestTemplate();

    @Cacheable(value = "tmdbSearch", key = "#query + '-' + #type", unless = "#result.isEmpty()")
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> search(String query, String type) {
        if (apiKey == null || apiKey.trim().isEmpty() || query == null || query.trim().isEmpty()) {
            return Collections.emptyList();
        }

        try {
            String path = "movie".equalsIgnoreCase(type) ? "search/movie" : "search/tv";
            String url = UriComponentsBuilder.fromHttpUrl(BASE_URL)
                    .pathSegment(path.split("/"))
                    .queryParam("api_key", apiKey.trim())
                    .queryParam("query", query.trim())
                    .queryParam("language", "en-US")
                    .toUriString();

            Map<String, Object> response = restTemplate.getForObject(new URI(url), Map.class);
            if (response != null && response.containsKey("results")) {
                List<Map<String, Object>> results = (List<Map<String, Object>>) response.get("results");
                if (results != null && !results.isEmpty()) {
                    return results;
                }
            }

            // Fallback to tr-TR if en-US returns empty
            String fallbackUrl = UriComponentsBuilder.fromHttpUrl(BASE_URL)
                    .pathSegment(path.split("/"))
                    .queryParam("api_key", apiKey.trim())
                    .queryParam("query", query.trim())
                    .queryParam("language", "tr-TR")
                    .toUriString();

            Map<String, Object> fallbackResp = restTemplate.getForObject(new URI(fallbackUrl), Map.class);
            if (fallbackResp != null && fallbackResp.containsKey("results")) {
                return (List<Map<String, Object>>) fallbackResp.get("results");
            }
        } catch (Exception e) {
            System.err.println("TMDB Search Error: " + e.getMessage());
        }
        return Collections.emptyList();
    }

    @Cacheable(value = "tmdbDetails", key = "#id + '-' + #type", unless = "#result.isEmpty()")
    @SuppressWarnings("unchecked")
    public Map<String, Object> getDetails(Integer id, String type) {
        if (apiKey == null || apiKey.trim().isEmpty() || id == null) {
            return Collections.emptyMap();
        }
        String endpoint = type.equalsIgnoreCase("movie") ? "/movie/" : "/tv/";
        URI uri = UriComponentsBuilder.fromUriString(BASE_URL + endpoint + id)
                .queryParam("api_key", apiKey.trim())
                .queryParam("language", "tr-TR")
                .build()
                .toUri();

        try {
            Map<String, Object> resp = restTemplate.getForObject(uri, Map.class);
            if (resp != null) return resp;
        } catch (Exception e) {
            System.err.println("TMDB Details Error: " + e.getMessage());
        }
        return Collections.emptyMap();
    }

    @Cacheable(value = "tmdbEnglishTitle", key = "#id + '-' + #type", unless = "#result == null || #result.isEmpty()")
    public String getEnglishTitle(Integer id, String type) {
        if (apiKey == null || apiKey.trim().isEmpty() || id == null || id <= 0) return null;
        try {
            String endpoint = "movie".equalsIgnoreCase(type) ? "/movie/" : "/tv/";
            URI uri = UriComponentsBuilder.fromUriString(BASE_URL + endpoint + id)
                    .queryParam("api_key", apiKey.trim())
                    .queryParam("language", "en-US")
                    .build()
                    .toUri();

            @SuppressWarnings("unchecked")
            Map<String, Object> resp = restTemplate.getForObject(uri, Map.class);
            if (resp != null) {
                String title = "movie".equalsIgnoreCase(type) ? (String) resp.get("title") : (String) resp.get("name");
                if (title != null && !title.isBlank()) {
                    String clean = extractLatinNameIfPresent(title);
                    if (!containsNonLatin(clean)) {
                        return clean.trim();
                    }
                }
                String orig = (String) resp.get("movie".equalsIgnoreCase(type) ? "original_title" : "original_name");
                if (orig != null && !orig.isBlank() && !containsNonLatin(orig)) {
                    return orig.trim();
                }
            }
        } catch (Exception e) {
            System.err.println("TMDB English Title Error (" + id + "): " + e.getMessage());
        }
        return null;
    }

    public String getPosterUrl(String posterPath) {
        if (posterPath == null || posterPath.isEmpty())
            return null;
        return IMAGE_BASE_URL + posterPath;
    }

    public String getProfileUrl(String profilePath) {
        if (profilePath == null || profilePath.isEmpty())
            return null;
        return PROFILE_BASE_URL + profilePath;
    }

    public static boolean containsNonLatin(String s) {
        if (s == null || s.isEmpty()) return false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            Character.UnicodeBlock block = Character.UnicodeBlock.of(c);
            if (block == Character.UnicodeBlock.HANGUL_SYLLABLES ||
                block == Character.UnicodeBlock.HANGUL_JAMO ||
                block == Character.UnicodeBlock.HANGUL_COMPATIBILITY_JAMO ||
                block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS ||
                block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A ||
                block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B ||
                block == Character.UnicodeBlock.CJK_SYMBOLS_AND_PUNCTUATION ||
                block == Character.UnicodeBlock.HIRAGANA ||
                block == Character.UnicodeBlock.KATAKANA ||
                block == Character.UnicodeBlock.THAI ||
                block == Character.UnicodeBlock.ARABIC ||
                block == Character.UnicodeBlock.CYRILLIC) {
                return true;
            }
        }
        return false;
    }

    public static String extractLatinNameIfPresent(String s) {
        if (s == null || s.isBlank()) return s;
        if (!containsNonLatin(s)) return s.trim();
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\(([A-Za-z0-9\\s.'’\\-]+)\\)").matcher(s);
        if (m.find()) {
            String extracted = m.group(1).trim();
            if (!extracted.isEmpty() && !containsNonLatin(extracted)) {
                return extracted;
            }
        }
        String cleaned = s.replaceAll("\\([^)]*\\)", "").trim();
        if (!cleaned.isEmpty() && !containsNonLatin(cleaned)) {
            return cleaned;
        }
        return s.trim();
    }

    public static String cleanCharacterName(String character, String actorName) {
        if (character == null || character.trim().isEmpty()) return null;
        String trimmed = character.trim();
        String lower = trimmed.toLowerCase();
        if (lower.equals("self") || lower.equals("himself") || lower.equals("herself") ||
            lower.equals("themself") || lower.equals("themselves") || lower.equals("kendisi") ||
            lower.equals("unknown") || lower.equals("n/a") || lower.equals("-") ||
            (actorName != null && lower.equals(actorName.trim().toLowerCase()))) {
            return null;
        }
        if (lower.startsWith("self - ") || lower.startsWith("self-") || lower.startsWith("self / ") || lower.startsWith("self: ")) {
            int dashIdx = trimmed.indexOf('-');
            if (dashIdx < 0) dashIdx = trimmed.indexOf('/');
            if (dashIdx < 0) dashIdx = trimmed.indexOf(':');
            if (dashIdx >= 0 && dashIdx + 1 < trimmed.length()) {
                String sub = trimmed.substring(dashIdx + 1).trim();
                return cleanCharacterName(sub, actorName);
            }
            return null;
        }
        return trimmed;
    }

}