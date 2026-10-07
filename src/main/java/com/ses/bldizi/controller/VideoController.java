package com.ses.bldizi.controller;

import com.ses.bldizi.model.CastMemberDto;
import com.ses.bldizi.repository.ActorRepository;
import com.ses.bldizi.repository.MovieRepository;
import com.ses.bldizi.repository.SeriesRepository;
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

    public VideoController(SeriesRepository seriesRepository,
                           MovieRepository movieRepository,
                           ActorRepository actorRepository,
                           JdbcTemplate jdbcTemplate) {
        this.seriesRepository = seriesRepository;
        this.movieRepository = movieRepository;
        this.actorRepository = actorRepository;
        this.jdbcTemplate = jdbcTemplate;
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
                if (series != null) {
                    response.put("title", series.get("name"));
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
                    response.put("adult", Boolean.TRUE.equals(episode.get("IsAdult")));
                }
            } else {
                response.put("title", episode.get("name"));
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
        List<Map<String, Object>> similar = seriesRepository.findSimilarContent(id, 12);
        return ResponseEntity.ok(similar);
    }

    @GetMapping({"/commercial/random", "/ad/random"})
    public ResponseEntity<Map<String, Object>> getRandomAd() {
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
        return ResponseEntity.ok(Map.of("probability", 0));
    }
}
