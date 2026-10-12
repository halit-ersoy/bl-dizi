package com.ses.bldizi.service;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.*;

@Service
public class TvMazeService {

    private final String BASE_URL = "https://api.tvmaze.com";
    private final RestTemplate restTemplate = new RestTemplate();

    @Cacheable(value = "tvmazeSearchSeries", key = "#query", unless = "#result.isEmpty()")
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> searchSeries(String query) {
        if (query == null || query.trim().isEmpty()) {
            return Collections.emptyList();
        }

        for (int attempt = 1; attempt <= 2; attempt++) {
            try {
                String url = UriComponentsBuilder.fromHttpUrl(BASE_URL)
                        .path("/search/shows")
                        .queryParam("q", query.trim())
                        .toUriString();

                return restTemplate.getForObject(new URI(url), List.class);
            } catch (HttpStatusCodeException e) {
                if (e.getStatusCode().value() == 429 && attempt == 1) {
                    try {
                        Thread.sleep(2000);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                    continue;
                }
                System.err.println("TVMaze Search Error: " + e.getMessage());
                break;
            } catch (Exception e) {
                System.err.println("TVMaze Search Error: " + e.getMessage());
                break;
            }
        }
        return Collections.emptyList();
    }

    @Cacheable(value = "tvmazeSeriesDetails", key = "#id", unless = "#result == null")
    @SuppressWarnings("unchecked")
    public Map<String, Object> getSeriesDetails(Integer id) {
        if (id == null) return Collections.emptyMap();
        try {
            String url = BASE_URL + "/shows/" + id;
            return restTemplate.getForObject(new URI(url), Map.class);
        } catch (Exception e) {
            System.err.println("TVMaze Details Error: " + e.getMessage());
            return Collections.emptyMap();
        }
    }
}
