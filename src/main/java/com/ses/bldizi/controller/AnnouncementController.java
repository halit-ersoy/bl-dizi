package com.ses.bldizi.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
public class AnnouncementController {

    private final JdbcTemplate jdbcTemplate;

    public AnnouncementController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping({"/api/announcements/active", "/api/settings/announcement"})
    public ResponseEntity<Map<String, Object>> getActiveAnnouncement() {
        Map<String, Object> response = new HashMap<>();
        try {
            String sql = "SELECT SettingKey, SettingValue FROM SystemSettings WHERE SettingKey IN ('announcement_text', 'announcement_active')";
            Map<String, String> map = new HashMap<>();
            jdbcTemplate.query(sql, rs -> {
                map.put(rs.getString("SettingKey"), rs.getString("SettingValue"));
            });

            String text = map.get("announcement_text");
            boolean active = "true".equalsIgnoreCase(map.get("announcement_active"));

            response.put("text", text != null ? text : "");
            response.put("active", active);
        } catch (Exception e) {
            response.put("text", "");
            response.put("active", false);
        }

        return ResponseEntity.ok(response);
    }
}
