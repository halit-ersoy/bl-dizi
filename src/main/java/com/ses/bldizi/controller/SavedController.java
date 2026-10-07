package com.ses.bldizi.controller;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/saved")
public class SavedController {

    private final JdbcTemplate jdbcTemplate;

    public SavedController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private String resolveCookie(String cookie, HttpServletRequest request) {
        if (cookie != null && !cookie.trim().isEmpty()) {
            return cookie.trim();
        }
        if (request != null && request.getCookies() != null) {
            for (Cookie c : request.getCookies()) {
                if ("wdiAuth".equals(c.getName()) && c.getValue() != null && !c.getValue().trim().isEmpty()) {
                    return c.getValue().trim();
                }
            }
        }
        return null;
    }

    private UUID resolveUserId(String cookie, HttpServletRequest request) {
        String token = resolveCookie(cookie, request);
        if (token == null || token.isEmpty()) {
            return null;
        }
        try {
            UUID possibleId = UUID.fromString(token);
            Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM Person WHERE ID = ?", Integer.class,
                    possibleId.toString());
            if (count != null && count > 0) return possibleId;
        } catch (Exception ignored) {}

        try {
            String sql = "SELECT ID FROM Person WHERE cookie = ?";
            String idStr = jdbcTemplate.queryForObject(sql, String.class, token);
            return idStr != null ? UUID.fromString(idStr) : null;
        } catch (Exception e) {
            return null;
        }
    }

    @GetMapping("/translated")
    public ResponseEntity<List<Map<String, Object>>> getTranslatedEpisodes() {
        try {
            List<Map<String, Object>> result = jdbcTemplate.queryForList("EXEC GetUpcoming 'Translated'");
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.ok(new ArrayList<>());
        }
    }

    @GetMapping("/loaded")
    public ResponseEntity<List<Map<String, Object>>> getLoadedEpisodes() {
        try {
            List<Map<String, Object>> result = jdbcTemplate.queryForList("EXEC GetUpcoming 'Loaded'");
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.ok(new ArrayList<>());
        }
    }

    @GetMapping("/lists")
    public ResponseEntity<?> getLists(
            @CookieValue(value = "wdiAuth", required = false) String cookie,
            HttpServletRequest request) {

        try {
            UUID userId = resolveUserId(cookie, request);
            if (userId == null) {
                return ResponseEntity.status(401)
                        .body(Map.of("Result", 0, "Message", "User not found or session expired"));
            }

            String sql = """
                    SELECT
                        L.Name as ListName,
                        L.ID as ListID,
                        I.VideoID,
                        COALESCE(M.name, S.name) as VideoName,
                        M.ReleaseYear as Year,
                        CASE
                            WHEN I.VideoType = 'movie' THEN (
                                SELECT STRING_AGG(C.Name, ', ')
                                FROM Categories C
                                JOIN MovieCategories MC ON MC.CategoryID = C.ID
                                WHERE MC.MovieID = M.ID
                            )
                            WHEN I.VideoType = 'series' THEN (
                                SELECT STRING_AGG(C.Name, ', ')
                                FROM Categories C
                                JOIN SeriesCategories SC ON SC.CategoryID = C.ID
                                WHERE SC.SeriesID = S.ID
                            )
                        END as Category,
                        COALESCE(M.slug, S.slug) as slug,
                        I.VideoType as Type
                    FROM UserLists L
                    LEFT JOIN UserListItems I ON I.ListID = L.ID
                    LEFT JOIN Movie M ON I.VideoID = M.ID AND I.VideoType = 'movie'
                    LEFT JOIN Series S ON I.VideoID = S.ID AND I.VideoType = 'series'
                    WHERE L.UserID = ?
                    ORDER BY L.Name
                    """;

            List<Map<String, Object>> lists = jdbcTemplate.queryForList(sql, userId.toString());
            return ResponseEntity.ok(lists);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("Result", 0, "Message", "Error: " + e.getMessage()));
        }
    }

    @PostMapping("/create")
    public ResponseEntity<?> createList(
            @RequestParam("title") String title,
            @CookieValue(value = "wdiAuth", required = false) String cookie,
            HttpServletRequest request) {

        try {
            UUID userId = resolveUserId(cookie, request);
            if (userId == null)
                return ResponseEntity.status(401).build();

            if (title == null || title.isBlank())
                return ResponseEntity.badRequest().body(Map.of("Result", 0, "Message", "Title cannot be empty"));

            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM UserLists WHERE UserID = ? AND Name = ?",
                    Integer.class, userId.toString(), title);

            if (count != null && count > 0) {
                return ResponseEntity.ok(Map.of("Result", 0, "Message", "List already exists"));
            }

            jdbcTemplate.update("INSERT INTO UserLists (UserID, Name) VALUES (?, ?)", userId.toString(), title);
            return ResponseEntity.ok(Map.of("Result", 1, "Message", "List created"));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("Result", 0, "Message", "Error: " + e.getMessage()));
        }
    }

    @PostMapping("/add")
    public ResponseEntity<?> addToList(
            @RequestParam("title") String title,
            @RequestParam("videoId") String videoIdStr,
            @CookieValue(value = "wdiAuth", required = false) String cookie,
            HttpServletRequest request) {

        try {
            UUID userId = resolveUserId(cookie, request);
            if (userId == null)
                return ResponseEntity.status(401).build();
            UUID videoId;
            try {
                videoId = UUID.fromString(videoIdStr);
            } catch (Exception e) {
                return ResponseEntity.ok(Map.of("Result", 0, "Message", "Geçersiz Video ID."));
            }

            List<Map<String, Object>> listRows = jdbcTemplate.queryForList(
                    "SELECT ID FROM UserLists WHERE UserID = ? AND Name = ?",
                    userId.toString(), title);

            if (listRows.isEmpty()) {
                return ResponseEntity.ok(Map.of("Result", 0, "Message", "List not found"));
            }

            Object listIdObj = listRows.get(0).get("ID");
            UUID listId = UUID.fromString(listIdObj.toString());

            String videoType = "movie";
            UUID targetId = videoId;

            Integer existMovie = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM Movie WHERE ID = ?", Integer.class,
                    videoId.toString());

            if (existMovie != null && existMovie > 0) {
                videoType = "movie";
            } else {
                Integer existSeries = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM Series WHERE ID = ?",
                        Integer.class, videoId.toString());
                if (existSeries != null && existSeries > 0) {
                    videoType = "series";
                } else {
                    try {
                        String seriesIdStr = jdbcTemplate.queryForObject("SELECT SeriesID FROM Episode WHERE ID = ?",
                                String.class, videoId.toString());
                        if (seriesIdStr != null) {
                            targetId = UUID.fromString(seriesIdStr);
                            videoType = "series";
                        } else {
                            return ResponseEntity.ok(Map.of("Result", 0, "Message", "Video not found"));
                        }
                    } catch (Exception e) {
                        return ResponseEntity.ok(Map.of("Result", 0, "Message", "Video not found"));
                    }
                }
            }

            Integer already = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM UserListItems WHERE ListID = ? AND VideoID = ?",
                    Integer.class, listId, targetId.toString());

            if (already != null && already > 0)
                return ResponseEntity.ok(Map.of("Result", 1, "Message", "Already in list"));

            jdbcTemplate.update(
                    "INSERT INTO UserListItems (ListID, VideoID, VideoType) VALUES (?, ?, ?)",
                    listId, targetId.toString(), videoType);

            return ResponseEntity.ok(Map.of("Result", 1, "Message", "Added to list"));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("Result", 0, "Message", "Error: " + e.getMessage()));
        }
    }

    @PostMapping("/remove")
    public ResponseEntity<?> removeFromList(
            @RequestParam("videoId") String videoIdStr,
            @CookieValue(value = "wdiAuth", required = false) String cookie,
            HttpServletRequest request) {

        try {
            UUID userId = resolveUserId(cookie, request);
            if (userId == null)
                return ResponseEntity.status(401).build();
            UUID videoId;
            try {
                videoId = UUID.fromString(videoIdStr);
            } catch (Exception e) {
                return ResponseEntity.ok(Map.of("Result", 0, "Message", "Geçersiz Video ID."));
            }
            UUID targetId = videoId;

            Integer existDirect = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM (SELECT ID FROM Movie UNION SELECT ID FROM Series) AS Combined WHERE ID = ?",
                    Integer.class, videoId.toString());

            if (existDirect != null && existDirect == 0) {
                try {
                    String seriesIdStr = jdbcTemplate.queryForObject("SELECT SeriesID FROM Episode WHERE ID = ?",
                            String.class, videoId.toString());
                    if (seriesIdStr != null) {
                        targetId = UUID.fromString(seriesIdStr);
                    }
                } catch (Exception ignored) {}
            }

            jdbcTemplate.update("""
                    DELETE FROM UserListItems
                    WHERE VideoID = ? AND ListID IN (SELECT ID FROM UserLists WHERE UserID = ?)
                    """, targetId.toString(), userId.toString());

            return ResponseEntity.ok(Map.of("Result", 1, "Message", "Removed from lists"));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("Result", 0, "Message", "Error: " + e.getMessage()));
        }
    }

    @PostMapping("/delete-list")
    public ResponseEntity<?> deleteList(
            @RequestParam("title") String title,
            @CookieValue(value = "wdiAuth", required = false) String cookie,
            HttpServletRequest request) {
        try {
            UUID userId = resolveUserId(cookie, request);
            if (userId == null)
                return ResponseEntity.status(401).build();

            jdbcTemplate.update("DELETE FROM UserLists WHERE UserID = ? AND Name = ?", userId.toString(), title);
            return ResponseEntity.ok(Map.of("Result", 1, "Message", "List deleted"));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("Result", 0, "Message", "Error: " + e.getMessage()));
        }
    }

    @PostMapping("/rename-list")
    public ResponseEntity<?> renameList(
            @RequestParam("title") String title,
            @RequestParam("newTitle") String newTitle,
            @CookieValue(value = "wdiAuth", required = false) String cookie,
            HttpServletRequest request) {
        try {
            UUID userId = resolveUserId(cookie, request);
            if (userId == null)
                return ResponseEntity.status(401).build();

            jdbcTemplate.update("UPDATE UserLists SET Name = ? WHERE UserID = ? AND Name = ?", newTitle,
                    userId.toString(), title);
            return ResponseEntity.ok(Map.of("Result", 1, "Message", "List renamed"));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("Result", 0, "Message", "Error: " + e.getMessage()));
        }
    }
}
