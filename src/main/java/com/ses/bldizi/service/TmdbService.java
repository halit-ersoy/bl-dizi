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
                    .queryParam("language", "tr-TR")
                    .toUriString();

            Map<String, Object> response = restTemplate.getForObject(new URI(url), Map.class);
            if (response != null && response.containsKey("results")) {
                List<Map<String, Object>> results = (List<Map<String, Object>>) response.get("results");
                if (results != null && !results.isEmpty()) {
                    return results;
                }
            }

            // Fallback to en-US if tr-TR returns empty
            String fallbackUrl = UriComponentsBuilder.fromHttpUrl(BASE_URL)
                    .pathSegment(path.split("/"))
                    .queryParam("api_key", apiKey.trim())
                    .queryParam("query", query.trim())
                    .queryParam("language", "en-US")
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
}
