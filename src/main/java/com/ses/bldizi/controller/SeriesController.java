package com.ses.bldizi.controller;

import com.ses.bldizi.model.EpisodeViewModel;
import com.ses.bldizi.model.PageResponse;
import com.ses.bldizi.model.VideoViewModel;
import com.ses.bldizi.repository.SeriesRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping({"/api/series", "/api/soapoperas"})
public class SeriesController {

    private final SeriesRepository seriesRepository;

    public SeriesController(SeriesRepository seriesRepository) {
        this.seriesRepository = seriesRepository;
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
}
