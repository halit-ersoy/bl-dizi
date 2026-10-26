package com.ses.bldizi.controller;

import com.ses.bldizi.model.ContentRequest;
import com.ses.bldizi.repository.ContentRequestRepository;
import com.ses.bldizi.repository.PersonRepository;
import com.ses.bldizi.service.TmdbService;
import com.ses.bldizi.service.TvMazeService;
import com.ses.bldizi.util.SlugUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@RestController
public class ContentRequestController {

    private final ContentRequestRepository contentRequestRepository;
    private final PersonRepository personRepository;
    private final TmdbService tmdbService;
    private final TvMazeService tvMazeService;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    public ContentRequestController(ContentRequestRepository contentRequestRepository,
                                    PersonRepository personRepository,
                                    TmdbService tmdbService,
                                    TvMazeService tvMazeService,
                                    JdbcTemplate jdbcTemplate) {
        this.contentRequestRepository = contentRequestRepository;
        this.personRepository = personRepository;
        this.tmdbService = tmdbService;
        this.tvMazeService = tvMazeService;
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/requests")
    public ResponseEntity<Resource> getRequestsPage() {
        try {
            Resource htmlPage = new ClassPathResource("static/requests/html/requests.html");
            if (!htmlPage.exists()) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_HTML_VALUE + ";charset=UTF-8")
                    .body(htmlPage);
        } catch (Exception e) {
            return ResponseEntity.status(500).build();
        }
    }

    @GetMapping("/api/requests")
    public ResponseEntity<?> getRequests(
            @CookieValue(name = "wdiAuth", required = false) String cookie,
            @RequestParam(value = "filter", defaultValue = "all") String filter,
            @RequestParam(value = "sort", defaultValue = "votes") String sort,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "15") int size) {
        try {
            UUID currentUserId = null;
            if (cookie != null && !cookie.trim().isEmpty()) {
                try {
                    Map<String, Object> userInfo = personRepository.getUserInfoByCookie(cookie);
                    if (userInfo != null && userInfo.get("ID") != null) {
                        currentUserId = UUID.fromString(userInfo.get("ID").toString());
                    }
                } catch (Exception ignored) {
                }
            }

            int offset = Math.max(0, (page - 1) * size);
            List<ContentRequest> requests = contentRequestRepository.getRequests(filter, sort, currentUserId, size, offset);
            int total = contentRequestRepository.countRequests(filter);
            int totalPages = (int) Math.ceil((double) total / size);

            Map<String, Object> response = new HashMap<>();
            response.put("items", requests);
            response.put("total", total);
            response.put("page", page);
            response.put("totalPages", totalPages);
            response.put("isAuthenticated", currentUserId != null);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @GetMapping("/api/requests/check-existing")
    public ResponseEntity<?> checkExisting(
            @RequestParam("title") String title,
            @RequestParam(value = "type", defaultValue = "series") String type) {
        if (title == null || title.trim().isEmpty()) {
            return ResponseEntity.ok(Map.of("alreadyExists", false));
        }
        Map<String, Object> localMatch = findExistingLocalContent(type, title.trim(), null);
        if (localMatch != null) {
            Object slugObj = localMatch.get("slug");
            String slug = (slugObj != null && !slugObj.toString().isEmpty()) ? slugObj.toString() : localMatch.get("ID").toString();
            return ResponseEntity.ok(Map.of(
                    "alreadyExists", true,
                    "existingSlug", slug,
                    "existingTitle", localMatch.get("name"),
                    "contentType", localMatch.get("contentType") != null ? localMatch.get("contentType") : type
            ));
        }
        return ResponseEntity.ok(Map.of("alreadyExists", false));
    }

    @PostMapping("/api/requests")
    public ResponseEntity<?> createRequest(
            @CookieValue(name = "wdiAuth", required = false) String cookie,
            @RequestBody Map<String, Object> body) {
        if (cookie == null || cookie.trim().isEmpty()) {
            return ResponseEntity.status(401)
                    .body(Map.of("success", false, "message", "İçerik isteğinde bulunmak için giriş yapmalısınız."));
        }

        try {
            Map<String, Object> userInfo = personRepository.getUserInfoByCookie(cookie);
            if (userInfo == null || userInfo.get("ID") == null) {
                return ResponseEntity.status(401)
                        .body(Map.of("success", false, "message", "Geçersiz oturum. Lütfen tekrar giriş yapın."));
            }

            Boolean isBanned = (Boolean) userInfo.get("isBanned");
            if (Boolean.TRUE.equals(isBanned)) {
                return ResponseEntity.status(403)
                        .body(Map.of("success", false, "message", "Hesabınız askıya alınmıştır."));
            }

            UUID userId = UUID.fromString(userInfo.get("ID").toString());
            String nickname = (String) userInfo.get("nickname");

            String title = (String) body.get("title");
            if (title == null || title.trim().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("success", false, "message", "Lütfen içerik adını belirtin."));
            }
            title = title.trim();
            if (title.length() > 255) {
                title = title.substring(0, 255);
            }

            String contentType = (String) body.get("contentType");
            if (contentType == null || (!contentType.equalsIgnoreCase("movie") && !contentType.equalsIgnoreCase("series"))) {
                contentType = "series";
            } else {
                contentType = contentType.toLowerCase();
            }

            // Check if item is already present on the platform
            Map<String, Object> localMatch = findExistingLocalContent(contentType, title, null);
            if (localMatch != null) {
                Object slugObj = localMatch.get("slug");
                String slug = (slugObj != null && !slugObj.toString().isEmpty()) ? slugObj.toString() : localMatch.get("ID").toString();
                String typeLabel = "movie".equalsIgnoreCase(contentType) ? "Film" : "Dizi";
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "alreadyExists", true,
                        "existingSlug", slug,
                        "message", "Bu " + typeLabel.toLowerCase() + " (\"" + localMatch.get("name") + "\") zaten sitemizde yayındadır. Yeni istek oluşturulamaz."
                ));
            }

            String posterUrl = (String) body.get("posterUrl");
            if (posterUrl != null) {
                posterUrl = posterUrl.trim();
                if (posterUrl.isEmpty()) posterUrl = null;
                else if (posterUrl.length() > 500) posterUrl = posterUrl.substring(0, 500);
            }

            Integer releaseYear = null;
            if (body.get("releaseYear") != null) {
                try {
                    releaseYear = Integer.parseInt(body.get("releaseYear").toString().trim());
                } catch (Exception ignored) {
                }
            }

            Integer tmdbId = null;
            if (body.get("tmdbId") != null) {
                try {
                    tmdbId = Integer.parseInt(body.get("tmdbId").toString().trim());
                } catch (Exception ignored) {
                }
            }

            Integer tvmazeId = null;
            if (body.get("tvmazeId") != null) {
                try {
                    tvmazeId = Integer.parseInt(body.get("tvmazeId").toString().trim());
                } catch (Exception ignored) {
                }
            }

            String description = (String) body.get("description");
            if (description != null) {
                description = description.trim();
                if (description.isEmpty()) description = null;
                else if (description.length() > 1000) description = description.substring(0, 1000);
            }

            ContentRequest created = contentRequestRepository.createRequest(
                    userId, nickname, title, contentType, posterUrl, releaseYear, tmdbId, tvmazeId, description);

            return ResponseEntity.ok(Map.of("success", true, "data", created));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @PostMapping("/api/requests/{id}/vote")
    public ResponseEntity<?> voteRequest(
            @CookieValue(name = "wdiAuth", required = false) String cookie,
            @PathVariable("id") String requestIdStr) {
        if (cookie == null || cookie.trim().isEmpty()) {
            return ResponseEntity.status(401)
                    .body(Map.of("success", false, "message", "Oy vermek için lütfen giriş yapın."));
        }

        try {
            Map<String, Object> userInfo = personRepository.getUserInfoByCookie(cookie);
            if (userInfo == null || userInfo.get("ID") == null) {
                return ResponseEntity.status(401)
                        .body(Map.of("success", false, "message", "Geçersiz oturum. Lütfen tekrar giriş yapın."));
            }

            Boolean isBanned = (Boolean) userInfo.get("isBanned");
            if (Boolean.TRUE.equals(isBanned)) {
                return ResponseEntity.status(403)
                        .body(Map.of("success", false, "message", "Hesabınız askıya alınmıştır."));
            }

            UUID userId = UUID.fromString(userInfo.get("ID").toString());
            UUID requestId = UUID.fromString(requestIdStr);

            Map<String, Object> voteResult = contentRequestRepository.toggleVote(requestId, userId);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("voted", voteResult.get("voted"));
            response.put("voteCount", voteResult.get("voteCount"));
            response.put("deleted", voteResult.getOrDefault("deleted", false));

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @GetMapping("/api/requests/search-external")
    public ResponseEntity<?> searchExternal(
            @RequestParam("q") String query,
            @RequestParam(value = "type", defaultValue = "all") String type) {
        if (query == null || query.trim().length() < 2) {
            return ResponseEntity.ok(Collections.emptyList());
        }

        String trimmedQuery = query.trim();
        List<Map<String, Object>> unifiedResults = new ArrayList<>();
        Set<String> seenKeys = new HashSet<>();

        CompletableFuture<List<Map<String, Object>>> tmdbMovieFuture = CompletableFuture.completedFuture(Collections.emptyList());
        CompletableFuture<List<Map<String, Object>>> tmdbSeriesFuture = CompletableFuture.completedFuture(Collections.emptyList());
        CompletableFuture<List<Map<String, Object>>> tvmazeFuture = CompletableFuture.completedFuture(Collections.emptyList());

        boolean searchMovies = "movie".equalsIgnoreCase(type) || "all".equalsIgnoreCase(type);
        boolean searchSeries = "series".equalsIgnoreCase(type) || "all".equalsIgnoreCase(type);

        if (searchMovies) {
            tmdbMovieFuture = CompletableFuture.supplyAsync(() -> tmdbService.search(trimmedQuery, "movie"));
        }
        if (searchSeries) {
            tmdbSeriesFuture = CompletableFuture.supplyAsync(() -> tmdbService.search(trimmedQuery, "tv"));
            tvmazeFuture = CompletableFuture.supplyAsync(() -> tvMazeService.searchSeries(trimmedQuery));
        }

        try {
            CompletableFuture.allOf(tmdbMovieFuture, tmdbSeriesFuture, tvmazeFuture).get(4, TimeUnit.SECONDS);
        } catch (Exception ignored) {
        }

        // 1. Process TMDB Movies
        try {
            List<Map<String, Object>> tmdbMovies = tmdbMovieFuture.getNow(Collections.emptyList());
            if (tmdbMovies != null) {
                for (Map<String, Object> item : tmdbMovies) {
                    String title = (String) item.get("title");
                    if (title == null) title = (String) item.get("original_title");
                    if (title == null) continue;

                    String origTitle = (String) item.get("original_title");
                    title = com.ses.bldizi.service.TmdbService.extractLatinNameIfPresent(title);
                    if (com.ses.bldizi.service.TmdbService.containsNonLatin(title) && origTitle != null && !origTitle.isBlank() && !com.ses.bldizi.service.TmdbService.containsNonLatin(origTitle)) {
                        title = com.ses.bldizi.service.TmdbService.extractLatinNameIfPresent(origTitle);
                    }
                    if (origTitle != null && com.ses.bldizi.service.TmdbService.containsNonLatin(origTitle)) {
                        origTitle = title;
                    }
                    Integer tmdbId = item.get("id") instanceof Number ? ((Number) item.get("id")).intValue() : null;
                    String posterPath = (String) item.get("poster_path");
                    String posterUrl = tmdbService.getPosterUrl(posterPath);
                    String releaseDate = (String) item.get("release_date");
                    Integer year = parseYear(releaseDate);
                    String overview = (String) item.get("overview");

                    String key = "movie:tmdb:" + (tmdbId != null ? tmdbId : title.toLowerCase());
                    if (seenKeys.add(key)) {
                        Map<String, Object> res = new HashMap<>();
                        res.put("title", title);
                        res.put("originalTitle", origTitle);
                        res.put("type", "movie");
                        res.put("typeLabel", "Film");
                        res.put("releaseYear", year);
                        res.put("posterUrl", posterUrl);
                        res.put("tmdbId", tmdbId);
                        res.put("tvmazeId", null);
                        res.put("overview", overview);
                        res.put("source", "TMDB");
                        unifiedResults.add(res);
                    }
                }
            }
        } catch (Exception ignored) {
        }

        // 2. Process TMDB Series
        try {
            List<Map<String, Object>> tmdbSeries = tmdbSeriesFuture.getNow(Collections.emptyList());
            if (tmdbSeries != null) {
                for (Map<String, Object> item : tmdbSeries) {
                    String title = (String) item.get("name");
                    if (title == null) title = (String) item.get("original_name");
                    if (title == null) continue;

                    String origTitle = (String) item.get("original_name");
                    title = com.ses.bldizi.service.TmdbService.extractLatinNameIfPresent(title);
                    if (com.ses.bldizi.service.TmdbService.containsNonLatin(title) && origTitle != null && !origTitle.isBlank() && !com.ses.bldizi.service.TmdbService.containsNonLatin(origTitle)) {
                        title = com.ses.bldizi.service.TmdbService.extractLatinNameIfPresent(origTitle);
                    }
                    if (origTitle != null && com.ses.bldizi.service.TmdbService.containsNonLatin(origTitle)) {
                        origTitle = title;
                    }
                    Integer tmdbId = item.get("id") instanceof Number ? ((Number) item.get("id")).intValue() : null;
                    String posterPath = (String) item.get("poster_path");
                    String posterUrl = tmdbService.getPosterUrl(posterPath);
                    String firstAirDate = (String) item.get("first_air_date");
                    Integer year = parseYear(firstAirDate);
                    String overview = (String) item.get("overview");

                    String key = "series:tmdb:" + (tmdbId != null ? tmdbId : title.toLowerCase());
                    if (seenKeys.add(key)) {
                        Map<String, Object> res = new HashMap<>();
                        res.put("title", title);
                        res.put("originalTitle", origTitle);
                        res.put("type", "series");
                        res.put("typeLabel", "Dizi");
                        res.put("releaseYear", year);
                        res.put("posterUrl", posterUrl);
                        res.put("tmdbId", tmdbId);
                        res.put("tvmazeId", null);
                        res.put("overview", overview);
                        res.put("source", "TMDB");
                        unifiedResults.add(res);
                    }
                }
            }
        } catch (Exception ignored) {
        }

        // 3. Process TVMaze Shows
        try {
            List<Map<String, Object>> tvmazeResults = tvmazeFuture.getNow(Collections.emptyList());
            if (tvmazeResults != null) {
                for (Map<String, Object> wrapper : tvmazeResults) {
                    if (wrapper.get("show") instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> show = (Map<String, Object>) wrapper.get("show");
                        String title = (String) show.get("name");
                        if (title == null) continue;
                        title = com.ses.bldizi.service.TmdbService.extractLatinNameIfPresent(title);

                        Integer tvmazeId = show.get("id") instanceof Number ? ((Number) show.get("id")).intValue() : null;
                        String posterUrl = null;
                        if (show.get("image") instanceof Map) {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> img = (Map<String, Object>) show.get("image");
                            posterUrl = (String) img.get("medium");
                            if (posterUrl == null) posterUrl = (String) img.get("original");
                        }

                        String premiered = (String) show.get("premiered");
                        Integer year = parseYear(premiered);
                        String summary = (String) show.get("summary");
                        if (summary != null) {
                            summary = summary.replaceAll("<[^>]*>", "").trim();
                        }

                        String key = "series:tvmaze:" + (tvmazeId != null ? tvmazeId : title.toLowerCase());
                        if (seenKeys.add(key)) {
                            Map<String, Object> res = new HashMap<>();
                            res.put("title", title);
                            res.put("originalTitle", null);
                            res.put("type", "series");
                            res.put("typeLabel", "Dizi");
                            res.put("releaseYear", year);
                            res.put("posterUrl", posterUrl);
                            res.put("tmdbId", null);
                            res.put("tvmazeId", tvmazeId);
                            res.put("overview", summary);
                            res.put("source", "TVmaze");
                            unifiedResults.add(res);
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }

        // Limit results to 20
        if (unifiedResults.size() > 20) {
            unifiedResults = unifiedResults.subList(0, 20);
        }

        // Check if each item already exists in local database
        for (Map<String, Object> item : unifiedResults) {
            String itemType = (String) item.get("type");
            String itemTitle = (String) item.get("title");
            String itemOrigTitle = (String) item.get("originalTitle");
            Map<String, Object> localMatch = findExistingLocalContent(itemType, itemTitle, itemOrigTitle);
            if (localMatch != null) {
                item.put("alreadyExists", true);
                Object slugObj = localMatch.get("slug");
                String slug = (slugObj != null && !slugObj.toString().isEmpty()) ? slugObj.toString() : localMatch.get("ID").toString();
                item.put("existingSlug", slug);
                item.put("existingTitle", localMatch.get("name"));
            } else {
                item.put("alreadyExists", false);
                item.put("existingSlug", null);
                item.put("existingTitle", null);
            }
        }

        return ResponseEntity.ok(unifiedResults);
    }

    @SuppressWarnings({"SqlResolve", "SqlNoDataSourceInspection"})
    public Map<String, Object> findExistingLocalContent(String type, String title, String originalTitle) {
        if (title == null || title.trim().isEmpty()) {
            return null;
        }
        String cleanTitle = title.trim();
        String cleanOriginal = (originalTitle != null && !originalTitle.trim().isEmpty() && !originalTitle.trim().equalsIgnoreCase(cleanTitle))
                ? originalTitle.trim() : null;

        String slug1 = SlugUtil.toSlug(cleanTitle);
        String slug2 = cleanOriginal != null ? SlugUtil.toSlug(cleanOriginal) : null;

        boolean checkMovie = "movie".equalsIgnoreCase(type) || "all".equalsIgnoreCase(type);
        boolean checkSeries = "series".equalsIgnoreCase(type) || "all".equalsIgnoreCase(type);

        if (checkMovie) {
            String sql = """
                SELECT TOP 1 CAST(ID AS NVARCHAR(36)) AS ID, name, slug FROM [dbo].[Movie]
                WHERE IsHidden = 0 AND (
                    LOWER(name) = LOWER(?)
                    OR (? IS NOT NULL AND LOWER(name) = LOWER(?))
                    OR (slug IS NOT NULL AND slug <> '' AND slug = ?)
                    OR (? IS NOT NULL AND slug IS NOT NULL AND slug <> '' AND slug = ?)
                )
            """;
            try {
                Map<String, Object> res = jdbcTemplate.queryForMap(sql, cleanTitle, cleanOriginal, cleanOriginal, slug1, slug2, slug2);
                if (res != null && !res.isEmpty()) {
                    res.put("contentType", "movie");
                    return res;
                }
            } catch (org.springframework.dao.EmptyResultDataAccessException ignored) {
            } catch (Exception e) {
                System.err.println("Local movie check error: " + e.getMessage());
            }
        }

        if (checkSeries) {
            String sql = """
                SELECT TOP 1 CAST(ID AS NVARCHAR(36)) AS ID, name, slug FROM [dbo].[Series]
                WHERE IsHidden = 0 AND (
                    LOWER(name) = LOWER(?)
                    OR (? IS NOT NULL AND LOWER(name) = LOWER(?))
                    OR (slug IS NOT NULL AND slug <> '' AND slug = ?)
                    OR (? IS NOT NULL AND slug IS NOT NULL AND slug <> '' AND slug = ?)
                )
            """;
            try {
                Map<String, Object> res = jdbcTemplate.queryForMap(sql, cleanTitle, cleanOriginal, cleanOriginal, slug1, slug2, slug2);
                if (res != null && !res.isEmpty()) {
                    res.put("contentType", "series");
                    return res;
                }
            } catch (org.springframework.dao.EmptyResultDataAccessException ignored) {
            } catch (Exception e) {
                System.err.println("Local series check error: " + e.getMessage());
            }
        }

        return null;
    }

    private Integer parseYear(String dateStr) {
        if (dateStr == null || dateStr.length() < 4) return null;
        try {
            return Integer.parseInt(dateStr.substring(0, 4));
        } catch (Exception e) {
            return null;
        }
    }
}
