package com.ses.bldizi.controller;

import com.ses.bldizi.model.CastMemberDto;
import com.ses.bldizi.repository.ActorRepository;
import com.ses.bldizi.repository.MovieRepository;
import com.ses.bldizi.repository.SeriesRepository;
import com.ses.bldizi.service.SystemSettingService;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/video")
public class VideoController {

    private final SeriesRepository seriesRepository;
    private final MovieRepository movieRepository;
    private final ActorRepository actorRepository;
    private final JdbcTemplate jdbcTemplate;
    private final SystemSettingService systemSettingService;

    public VideoController(SeriesRepository seriesRepository,
                           MovieRepository movieRepository,
                           ActorRepository actorRepository,
                           JdbcTemplate jdbcTemplate,
                           SystemSettingService systemSettingService) {
        this.seriesRepository = seriesRepository;
        this.movieRepository = movieRepository;
        this.actorRepository = actorRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.systemSettingService = systemSettingService;
    }

    @GetMapping("/details")
    public ResponseEntity<Map<String, Object>> getVideoDetails(@RequestParam("id") UUID id) {
        Map<String, Object> response = new HashMap<>();

        // 1. Try Episode
        Map<String, Object> episode = seriesRepository.findEpisodeDetails(id);
        if (episode != null) {
            String seriesIdStr = (String) episode.get("SeriesId");
            UUID seriesId = seriesIdStr != null ? UUID.fromString(seriesIdStr) : null;
            List<CastMemberDto> cast = Collections.emptyList();

            if (seriesId != null) {
                Map<String, Object> series = seriesRepository.findSeriesDetails(seriesId);
                int totalEps = seriesRepository.countEpisodesBySeriesId(seriesId);
                response.put("totalEpisodes", totalEps);
                if (series != null) {
                    String seriesName = (String) series.get("name");
                    Object seasonObj = episode.get("SeasonNumber");
                    Object epObj = episode.get("EpisodeNumber");
                    String formattedTitle = seriesName;
                    if (seasonObj != null && epObj != null) {
                        formattedTitle = seriesName + " - " + seasonObj + ". Sezon " + epObj + ". Bölüm";
                    }
                    response.put("title", formattedTitle);
                    response.put("seriesTitle", seriesName);
                    response.put("name", episode.get("name"));
                    Object duration = episode.get("DurationMinutes");
                    response.put("duration", duration != null && ((Number) duration).intValue() > 0 ? duration + " dk" : "");
                    response.put("plot", series.get("Summary"));
                    String categoryStr = (String) series.get("Category");
                    response.put("genres", categoryStr != null ? List.of(categoryStr.split(",")) : List.of());
                    response.put("language", series.get("Language"));
                    response.put("country", series.get("Country"));
                    response.put("finalStatus", series.get("finalStatus"));
                    response.put("adult", Boolean.TRUE.equals(episode.get("IsAdult")));
                    cast = actorRepository.getCastForSeries(seriesId);
                } else {
                    response.put("title", episode.get("name"));
                    response.put("seriesTitle", episode.get("name"));
                    response.put("adult", Boolean.TRUE.equals(episode.get("IsAdult")));
                }
            } else {
                response.put("title", episode.get("name"));
                response.put("seriesTitle", episode.get("name"));
                response.put("adult", Boolean.TRUE.equals(episode.get("IsAdult")));
            }

            response.put("cast", cast);
            response.put("season", episode.get("SeasonNumber"));
            response.put("episode", episode.get("EpisodeNumber"));
            response.put("type", "episode");
            response.put("seriesId", seriesIdStr);
            response.put("slug", episode.get("slug"));
            return ResponseEntity.ok(response);
        }

        // 2. Try Movie
        Map<String, Object> movie = movieRepository.findMovieDetails(id);
        if (movie != null) {
            response.put("title", movie.get("name"));
            Object duration = movie.get("DurationMinutes");
            response.put("duration", duration != null && ((Number) duration).intValue() > 0 ? duration + " dk" : "");
            response.put("year", movie.get("ReleaseYear"));
            response.put("plot", movie.get("Summary"));
            String categoryStr = (String) movie.get("Category");
            response.put("genres", categoryStr != null ? List.of(categoryStr.split(",")) : List.of());
            response.put("language", movie.get("Language"));
            response.put("country", movie.get("Country"));
            response.put("type", "movie");
            response.put("slug", movie.get("slug"));
            response.put("adult", Boolean.TRUE.equals(movie.get("IsAdult")));

            List<CastMemberDto> movieCast = actorRepository.getCastForMovie(id);
            response.put("cast", movieCast);
            return ResponseEntity.ok(response);
        }

        // 3. Try Upcoming
        try {
            List<Map<String, Object>> upcomings = jdbcTemplate.queryForList(
                    "SELECT TOP 1 * FROM Upcoming WHERE ID = ? AND (isBl = 1 OR Genre IN ('BL', 'GL'))", id);
            if (!upcomings.isEmpty()) {
                Map<String, Object> u = upcomings.get(0);
                String title = (String) u.get("name");
                String upcomingType = (String) u.get("type");
                boolean isMovie = upcomingType != null && upcomingType.equalsIgnoreCase("Movie");
                Object refIdObj = u.get("ReferenceId");
                UUID refId = refIdObj != null ? UUID.fromString(refIdObj.toString()) : null;

                response.put("id", u.get("ID").toString());
                response.put("title", title);
                response.put("name", title);
                response.put("type", "upcoming");
                response.put("isUpcoming", true);
                response.put("category", upcomingType);
                response.put("datetime", u.get("datetime") != null ? u.get("datetime").toString() : "");
                response.put("status", u.get("status"));
                response.put("poster", "/media/image/" + u.get("ID"));
                response.put("adult", false);

                String plot = (String) u.get("Summary");
                Object year = u.get("ReleaseYear");
                String country = (String) u.get("Country");
                String language = (String) u.get("Language");
                String duration = (String) u.get("Duration");
                String slug = (String) u.get("Slug");
                List<String> genres = new ArrayList<>();
                List<CastMemberDto> cast = Collections.emptyList();

                Map<String, Object> linkedSeries = null;
                Map<String, Object> linkedMovie = null;

                if (refId != null) {
                    linkedSeries = seriesRepository.findSeriesDetails(refId);
                    if (linkedSeries == null) {
                        linkedMovie = movieRepository.findMovieDetails(refId);
                    }
                    if (linkedSeries == null && linkedMovie == null) {
                        Map<String, Object> ep = seriesRepository.findEpisodeDetails(refId);
                        if (ep != null && ep.get("SeriesId") != null) {
                            linkedSeries = seriesRepository.findSeriesDetails(UUID.fromString(ep.get("SeriesId").toString()));
                        }
                    }
                }

                if (linkedSeries == null && linkedMovie == null && title != null) {
                    try {
                        String sId = jdbcTemplate.queryForObject("SELECT TOP 1 CAST(ID AS NVARCHAR(36)) FROM Series WHERE Name = ?", String.class, title);
                        if (sId != null) linkedSeries = seriesRepository.findSeriesDetails(UUID.fromString(sId));
                    } catch (Exception ignored) {}

                    if (linkedSeries == null) {
                        try {
                            String mId = jdbcTemplate.queryForObject("SELECT TOP 1 CAST(ID AS NVARCHAR(36)) FROM Movie WHERE Name = ?", String.class, title);
                            if (mId != null) linkedMovie = movieRepository.findMovieDetails(UUID.fromString(mId));
                        } catch (Exception ignored) {}
                    }
                }

                if (linkedSeries != null) {
                    if (plot == null || plot.isBlank()) plot = (String) linkedSeries.get("Summary");
                    if (country == null || country.isBlank()) country = (String) linkedSeries.get("Country");
                    if (language == null || language.isBlank()) language = (String) linkedSeries.get("Language");
                    if (slug == null || slug.isBlank()) slug = (String) linkedSeries.get("slug");
                    String catStr = (String) linkedSeries.get("Category");
                    if (catStr != null && !catStr.isBlank()) {
                        genres = List.of(catStr.split(","));
                    }
                    if (linkedSeries.get("ID") != null) {
                        cast = actorRepository.getCastForSeries(UUID.fromString(linkedSeries.get("ID").toString()));
                    }
                } else if (linkedMovie != null) {
                    if (plot == null || plot.isBlank()) plot = (String) linkedMovie.get("Summary");
                    if (year == null) year = linkedMovie.get("ReleaseYear");
                    if (country == null || country.isBlank()) country = (String) linkedMovie.get("Country");
                    if (language == null || language.isBlank()) language = (String) linkedMovie.get("Language");
                    if (duration == null || duration.isBlank()) {
                        Object dur = linkedMovie.get("DurationMinutes");
                        duration = (dur != null && ((Number) dur).intValue() > 0) ? dur + " dk" : "";
                    }
                    if (slug == null || slug.isBlank()) slug = (String) linkedMovie.get("slug");
                    String catStr = (String) linkedMovie.get("Category");
                    if (catStr != null && !catStr.isBlank()) {
                        genres = List.of(catStr.split(","));
                    }
                    if (linkedMovie.get("ID") != null) {
                        cast = actorRepository.getCastForMovie(UUID.fromString(linkedMovie.get("ID").toString()));
                    }
                }

                if (plot == null || plot.isBlank()) {
                    plot = "Bu yapım çok yakında BL Dizi platformunda yayınlanacaktır. Yayın tarihini ve geri sayımı takip edebilirsiniz.";
                }
                if (genres.isEmpty()) {
                    String g = (String) u.get("Genre");
                    if (g != null && !g.isBlank() && !g.equalsIgnoreCase("Normal")) {
                        genres = List.of(isMovie ? "Film" : "Dizi", g);
                    } else {
                        genres = List.of(isMovie ? "Film" : "Dizi");
                    }
                }
                if (year == null && u.get("datetime") != null) {
                    try {
                        java.sql.Timestamp ts = (java.sql.Timestamp) u.get("datetime");
                        year = ts.toInstant().atZone(java.time.ZoneId.systemDefault()).getYear();
                    } catch (Exception ignored) {}
                }

                response.put("plot", plot);
                response.put("year", year != null ? year.toString() : "");
                response.put("country", country != null ? country : "");
                response.put("language", language != null ? language : "");
                response.put("duration", duration != null ? duration : "");
                response.put("slug", slug != null ? slug : id.toString());
                response.put("genres", genres);
                response.put("cast", cast);

                return ResponseEntity.ok(response);
            }
        } catch (Exception ignored) {
        }

        return ResponseEntity.notFound().build();
    }

    @GetMapping("/resolve-slug")
    public ResponseEntity<Map<String, String>> resolveSlug(@RequestParam("slug") String slug) {
        boolean isUuid = false;
        try {
            UUID.fromString(slug);
            isUuid = true;
        } catch (IllegalArgumentException ignored) {
        }

        if (isUuid) {
            UUID id = UUID.fromString(slug);

            Map<String, Object> ep = seriesRepository.findEpisodeDetails(id);
            if (ep != null) {
                return ResponseEntity.ok(Map.of("id", ep.get("ID").toString(), "type", "episode"));
            }

            Map<String, Object> s = seriesRepository.findSeriesDetails(id);
            if (s != null) {
                UUID firstEpId = seriesRepository.findFirstEpisodeIdBySeriesId(id);
                if (firstEpId != null) {
                    return ResponseEntity.ok(Map.of("id", firstEpId.toString(), "type", "episode"));
                }
            }

            Map<String, Object> m = movieRepository.findMovieDetails(id);
            if (m != null) {
                return ResponseEntity.ok(Map.of("id", m.get("ID").toString(), "type", "movie"));
            }

            // Check Upcoming by ID
            try {
                List<Map<String, Object>> upcomings = jdbcTemplate.queryForList(
                        "SELECT TOP 1 ID FROM Upcoming WHERE ID = ? AND (isBl = 1 OR Genre IN ('BL', 'GL'))", id);
                if (!upcomings.isEmpty()) {
                    return ResponseEntity.ok(Map.of("id", id.toString(), "type", "upcoming"));
                }
            } catch (Exception ignored) {
            }

            return ResponseEntity.notFound().build();
        }

        // Slug based
        Map<String, Object> episode = seriesRepository.findEpisodeBySlug(slug);
        if (episode != null) {
            return ResponseEntity.ok(Map.of("id", episode.get("ID").toString(), "type", "episode"));
        }

        Map<String, Object> movie = movieRepository.findMovieBySlug(slug);
        if (movie != null) {
            return ResponseEntity.ok(Map.of("id", movie.get("ID").toString(), "type", "movie"));
        }

        Map<String, Object> series = seriesRepository.findSeriesBySlug(slug);
        if (series != null) {
            UUID seriesId = UUID.fromString((String) series.get("ID"));
            UUID firstEpId = seriesRepository.findFirstEpisodeIdBySeriesId(seriesId);
            if (firstEpId != null) {
                return ResponseEntity.ok(Map.of("id", firstEpId.toString(), "type", "episode"));
            }
        }

        // Check Upcoming by slug or name
        try {
            List<Map<String, Object>> upcomings = jdbcTemplate.queryForList(
                    "SELECT TOP 1 ID FROM Upcoming WHERE (slug = ? OR name = ?) AND (isBl = 1 OR Genre IN ('BL', 'GL'))", slug, slug);
            if (!upcomings.isEmpty()) {
                return ResponseEntity.ok(Map.of("id", upcomings.get(0).get("ID").toString(), "type", "upcoming"));
            }
        } catch (Exception ignored) {
        }

        return ResponseEntity.notFound().build();
    }

    @PostMapping("/increment-view")
    public ResponseEntity<?> incrementViewCount(@RequestParam("id") UUID id) {
        try {
            seriesRepository.incrementViewCount(id);
            movieRepository.incrementViewCount(id);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Failed to increment view count");
        }
    }

    @GetMapping("/similar")
    public ResponseEntity<List<Map<String, Object>>> getSimilarContent(@RequestParam("id") UUID id) {
        UUID contentIdForSearch = id;
        try {
            List<Map<String, Object>> upcomings = jdbcTemplate.queryForList(
                    "SELECT TOP 1 * FROM Upcoming WHERE ID = ? AND (isBl = 1 OR Genre IN ('BL', 'GL'))", id);
            if (!upcomings.isEmpty()) {
                Map<String, Object> u = upcomings.get(0);
                Object refIdObj = u.get("ReferenceId");
                if (refIdObj != null) {
                    contentIdForSearch = UUID.fromString(refIdObj.toString());
                } else {
                    String name = (String) u.get("name");
                    try {
                        String sId = jdbcTemplate.queryForObject("SELECT TOP 1 CAST(ID AS NVARCHAR(36)) FROM Series WHERE Name = ? AND (SeriesType IN ('BL', 'GL') OR isBl = 1)", String.class, name);
                        if (sId != null) contentIdForSearch = UUID.fromString(sId);
                    } catch (Exception ignored) {}
                }
            }
        } catch (Exception ignored) {
        }

        List<Map<String, Object>> similar = seriesRepository.findSimilarContent(contentIdForSearch, 12);
        return ResponseEntity.ok(similar);
    }

    @GetMapping({"/commercial/random", "/ad/random"})
    public ResponseEntity<Map<String, Object>> getRandomAd() {
        int probability = systemSettingService != null ? systemSettingService.getAdProbability() : 30;
        if (probability <= 0) {
            return ResponseEntity.noContent().build();
        }
        int roll = new java.util.Random().nextInt(100);
        if (roll >= probability) {
            return ResponseEntity.noContent().build();
        }

        try {
            List<Map<String, Object>> ads = jdbcTemplate.queryForList(
                    "SELECT TOP 1 ID, Name FROM Ad WHERE IsHidden = 0 ORDER BY NEWID()");
            if (!ads.isEmpty()) {
                Map<String, Object> ad = ads.get(0);
                return ResponseEntity.ok(Map.of("id", ad.get("ID"), "name", ad.get("Name")));
            }
        } catch (Exception ignored) {}
        return ResponseEntity.noContent().build();
    }

    @GetMapping({"/commercial/probability", "/ad/probability"})
    public ResponseEntity<Map<String, Object>> getAdProbability() {
        int probability = systemSettingService != null ? systemSettingService.getAdProbability() : 30;
        return ResponseEntity.ok(Map.of("probability", probability));
    }
}

