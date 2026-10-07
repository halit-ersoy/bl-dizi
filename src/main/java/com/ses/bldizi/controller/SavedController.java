package com.ses.bldizi.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/saved")
public class SavedController {

    private final JdbcTemplate jdbcTemplate;

    public SavedController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
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
}
