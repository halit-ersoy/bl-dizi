package com.ses.bldizi.controller;

import com.ses.bldizi.model.CastMemberDto;
import com.ses.bldizi.model.EpisodeViewModel;
import com.ses.bldizi.model.PageResponse;
import com.ses.bldizi.model.VideoViewModel;
import com.ses.bldizi.repository.ActorRepository;
import com.ses.bldizi.repository.SeriesRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping({"/api/series", "/api/soapoperas"})
public class SeriesController {

    private final SeriesRepository seriesRepository;
    private final ActorRepository actorRepository;

    public SeriesController(SeriesRepository seriesRepository, ActorRepository actorRepository) {
        this.seriesRepository = seriesRepository;
        this.actorRepository = actorRepository;
    }

    @GetMapping("/recent")
    public ResponseEntity<PageResponse<VideoViewModel>> getRecentSeriesPaged(
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "18") int size) {

        int offset = Math.max(0, (page - 1) * size);
        List<VideoViewModel> series = seriesRepository.findRecentSeriesPaged(offset, size);
        int totalElements = seriesRepository.countAllSeries();
        int totalPages = (int) Math.ceil((double) totalElements / size);

        return ResponseEntity.ok(new PageResponse<>(series, totalPages, page, totalElements));
    }

    @GetMapping("/search")
    public ResponseEntity<PageResponse<VideoViewModel>> searchSeriesPaged(
            @RequestParam(value = "query", defaultValue = "") String query,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "18") int size) {

        if (query == null || query.trim().isEmpty()) {
            return getRecentSeriesPaged(page, size);
        }

        int offset = Math.max(0, (page - 1) * size);
        List<VideoViewModel> series = seriesRepository.searchSeriesPaged(query, offset, size);
        int totalElements = seriesRepository.countSeriesBySearch(query);
        int totalPages = (int) Math.ceil((double) totalElements / size);

        return ResponseEntity.ok(new PageResponse<>(series, totalPages, page, totalElements));
    }

    @GetMapping("/filter")
    public ResponseEntity<PageResponse<VideoViewModel>> filterSeriesPaged(
            @RequestParam(value = "subType", defaultValue = "all") String subType,
            @RequestParam(value = "categoryId", required = false) Integer categoryId,
            @RequestParam(value = "finalStatus", required = false) Integer finalStatus,
            @RequestParam(value = "year", required = false) Integer year,
            @RequestParam(value = "country", required = false) String country,
            @RequestParam(value = "sort", defaultValue = "newest") String sort,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "18") int size) {

        int offset = Math.max(0, (page - 1) * size);
        List<VideoViewModel> filteredSeries = seriesRepository.findSeriesWithFilters(
                subType, categoryId, finalStatus, year, country, sort, offset, size);
        int totalElements = seriesRepository.countSeriesWithFilters(
                subType, categoryId, finalStatus, year, country);
        int totalPages = (int) Math.ceil((double) totalElements / size);

        return ResponseEntity.ok(new PageResponse<>(filteredSeries, totalPages, page, totalElements));
    }

    @GetMapping("/{id}/episodes")
    public ResponseEntity<List<EpisodeViewModel>> getEpisodes(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(seriesRepository.findEpisodesBySeriesId(id));
    }

    @GetMapping("/episode/{id}/parent")
    public ResponseEntity<Map<String, Object>> getParentSeries(@PathVariable("id") UUID episodeId) {
        Map<String, Object> series = seriesRepository.findSeriesByEpisodeId(episodeId);
        if (series == null) {
            return ResponseEntity.notFound().build();
        }
        Map<String, Object> resp = new HashMap<>(series);
        if (resp.containsKey("ID")) {
            resp.put("id", resp.get("ID"));
        }
        return ResponseEntity.ok(resp);
    }

    @GetMapping({ "/{idOrSlug}/details", "/details" })
    public ResponseEntity<Map<String, Object>> getSeriesDetails(
            @PathVariable(value = "idOrSlug", required = false) String idOrSlugPath,
            @RequestParam(value = "id", required = false) String idParam,
            @RequestParam(value = "slug", required = false) String slugParam) {

        String identifier = (idOrSlugPath != null && !idOrSlugPath.isBlank()) ? idOrSlugPath.trim() : null;
        if (identifier == null) {
            identifier = (slugParam != null && !slugParam.isBlank()) ? slugParam.trim() : (idParam != null ? idParam.trim() : null);
        }
        if (identifier == null || identifier.isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        Map<String, Object> series = null;
        UUID uid = null;
        try {
            uid = UUID.fromString(identifier);
            series = seriesRepository.findSeriesDetails(uid);
        } catch (IllegalArgumentException ignored) {
        }

        if (series == null) {
            series = seriesRepository.findSeriesBySlug(identifier);
            if (series != null && series.get("ID") != null) {
                try {
                    uid = UUID.fromString(series.get("ID").toString());
                } catch (Exception ignored) {
                }
            }
        }

        if (series == null) {
            return ResponseEntity.notFound().build();
        }

        List<EpisodeViewModel> episodes = (uid != null) ? seriesRepository.findEpisodesBySeriesId(uid) : Collections.emptyList();
        List<CastMemberDto> cast = (uid != null) ? actorRepository.getCastForSeries(uid) : Collections.emptyList();

        Map<String, Object> response = new HashMap<>();
        String seriesIdStr = (uid != null) ? uid.toString() : (series.get("ID") != null ? series.get("ID").toString() : "");
        response.put("id", seriesIdStr);
        response.put("name", series.get("name"));
        response.put("title", series.get("name"));
        response.put("summary", series.get("Summary"));
        response.put("category", series.get("Category"));
        response.put("language", series.get("Language"));
        response.put("country", series.get("Country"));
        response.put("seriesType", series.get("SeriesType") != null ? series.get("SeriesType") : "BL");
        response.put("finalStatus", series.get("finalStatus"));
        response.put("slug", series.get("slug"));
        response.put("uploadDate", series.get("uploadDate"));
        response.put("totalEpisodes", episodes != null ? episodes.size() : 0);
        response.put("posterUrl", "/media/image/" + seriesIdStr);
        response.put("backdropUrl", "/media/image/" + seriesIdStr);
        response.put("episodes", episodes != null ? episodes : Collections.emptyList());
        response.put("cast", cast != null ? cast : Collections.emptyList());

        if (episodes != null && !episodes.isEmpty()) {
            EpisodeViewModel firstEp = episodes.get(0);
            response.put("firstEpisodeUrl", (firstEp.getSlug() != null && !firstEp.getSlug().isBlank()) ? "/" + firstEp.getSlug() : "/" + firstEp.getId());
            EpisodeViewModel lastEp = episodes.get(episodes.size() - 1);
            response.put("lastEpisodeUrl", (lastEp.getSlug() != null && !lastEp.getSlug().isBlank()) ? "/" + lastEp.getSlug() : "/" + lastEp.getId());
        }

        return ResponseEntity.ok(response);
    }
}
