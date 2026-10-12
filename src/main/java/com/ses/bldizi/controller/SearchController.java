package com.ses.bldizi.controller;

import com.ses.bldizi.repository.SeriesRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/search")
public class SearchController {

    private final SeriesRepository seriesRepository;

    public SearchController(SeriesRepository seriesRepository) {
        this.seriesRepository = seriesRepository;
    }

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> search(@RequestParam("q") String query) {
        if (query == null || query.trim().length() < 2) {
            return ResponseEntity.ok(Collections.emptyList());
        }
        return ResponseEntity.ok(seriesRepository.searchBlContent(query));
    }
}
