package com.ses.bldizi.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/updates")
public class UpdateNoteController {

    private final JdbcTemplate jdbcTemplate;

    public UpdateNoteController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> getActiveUpdates() {
        try {
            String sql = "SELECT ID, title, message, createdAt, isActive FROM UpdateNotes WHERE isActive = 1 ORDER BY createdAt DESC";
            List<Map<String, Object>> rows = jdbcTemplate.query(sql, (rs, rowNum) -> {
                Map<String, Object> map = new HashMap<>();
                map.put("id", rs.getString("ID"));
                map.put("title", rs.getString("title"));
                map.put("message", rs.getString("message"));
                map.put("createdAt", rs.getTimestamp("createdAt"));
                map.put("active", rs.getBoolean("isActive"));
                return map;
            });
            return ResponseEntity.ok(rows);
        } catch (Exception e) {
            return ResponseEntity.ok(new ArrayList<>());
        }
    }
}
