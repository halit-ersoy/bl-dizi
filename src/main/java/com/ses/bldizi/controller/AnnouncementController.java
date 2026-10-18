package com.ses.bldizi.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@SuppressWarnings({"SqlResolve", "SqlNoDataSourceInspection"})
public class AnnouncementController {

    private final JdbcTemplate jdbcTemplate;

    public AnnouncementController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/api/settings/ads")
    public ResponseEntity<Map<String, Object>> getAdSettings() {
        Map<String, Object> response = new HashMap<>();
        try {
            String sql = "SELECT SettingKey, SettingValue FROM SystemSettings WHERE SettingKey LIKE 'ad_%'";
            Map<String, String> map = new HashMap<>();
            jdbcTemplate.query(sql, rs -> {
                map.put(rs.getString("SettingKey"), rs.getString("SettingValue"));
            });

            boolean videoAds = !"false".equalsIgnoreCase(map.get("ad_video_enabled"));
            boolean socialBar = !"false".equalsIgnoreCase(map.get("ad_socialbar_enabled"));
            boolean skyscraperAds = !"false".equalsIgnoreCase(map.get("ad_skyscraper_enabled"));
            boolean bannerAds = !"false".equalsIgnoreCase(map.get("ad_banner_enabled"));
            boolean supportButton = !"false".equalsIgnoreCase(map.get("ad_support_btn_enabled"));

            int probability = 30;
            if (map.containsKey("ad_probability")) {
                try {
                    probability = Integer.parseInt(map.get("ad_probability").trim());
                } catch (Exception ignored) {}
            }

            response.put("videoAds", videoAds);
            response.put("socialBar", socialBar);
            response.put("skyscraperAds", skyscraperAds);
            response.put("bannerAds", bannerAds);
            response.put("supportButton", supportButton);
            response.put("probability", probability);
        } catch (Exception e) {
            response.put("videoAds", true);
            response.put("socialBar", true);
            response.put("skyscraperAds", true);
            response.put("bannerAds", true);
            response.put("supportButton", true);
            response.put("probability", 30);
        }
        return ResponseEntity.ok(response);
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
