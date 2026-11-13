package com.ses.bldizi.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;

@SuppressWarnings({"SqlResolve", "SqlRedundantOrderingDirection"})
@RestController
public class CalendarController {

    private final JdbcTemplate jdbcTemplate;

    public CalendarController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @SuppressWarnings({"SqlResolve", "SqlRedundantOrderingDirection"})
    @GetMapping("/api/calendar")
    public ResponseEntity<Map<String, List<Map<String, Object>>>> getCalendar(
            @RequestParam(value = "platform", required = false) String platform) {
        Map<String, List<Map<String, Object>>> calendarData = new LinkedHashMap<>();
        String[] days = { "monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday" };
        for (String day : days) {
            calendarData.put(day, new ArrayList<>());
        }

        try {
            //noinspection SqlResolve,SqlRedundantOrderingDirection
            String sql;
            if ("all".equalsIgnoreCase(platform)) {
                sql = "SELECT * FROM CalendarEvent ORDER BY sortOrder, showTime";
            } else if ("whodatidols".equalsIgnoreCase(platform)) {
                sql = "SELECT * FROM CalendarEvent WHERE (isBl = 0 OR isBl IS NULL) AND (platform IN ('whodatidols', 'all', 'both') OR platform IS NULL) ORDER BY sortOrder, showTime";
            } else {
                // Default to BL Dizi
                sql = "SELECT * FROM CalendarEvent WHERE isBl = 1 OR platform IN ('bldizi', 'all', 'both') ORDER BY sortOrder, showTime";
            }

            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql);

            for (Map<String, Object> row : rows) {
                Object dayObj = row.get("dayOfWeek");
                if (dayObj != null) {
                    String day = dayObj.toString().toLowerCase().trim();
                    if (calendarData.containsKey(day)) {
                        Map<String, Object> event = new HashMap<>();
                        event.put("id", row.get("ID"));
                        event.put("dayOfWeek", row.get("dayOfWeek"));
                        event.put("title", row.get("title"));
                        event.put("episode", row.get("episode"));
                        event.put("time", row.get("showTime"));
                        event.put("sortOrder", row.get("sortOrder"));
                        event.put("posterUrl", row.get("posterUrl"));

                        String plat = (String) row.get("platform");
                        Boolean isBl = (Boolean) row.get("isBl");
                        if (plat == null || plat.isBlank()) {
                            plat = Boolean.TRUE.equals(isBl) ? "bldizi" : "whodatidols";
                        }
                        event.put("platform", plat);
                        event.put("isBl", Boolean.TRUE.equals(isBl));
                        event.put("channel", row.get("channel"));
                        event.put("category", row.get("category"));
                        event.put("summary", row.get("summary"));
                        event.put("slug", row.get("slug"));
                        event.put("seriesId", row.get("seriesId"));
                        event.put("tmdbId", row.get("tmdbId"));
                        event.put("tvmazeId", row.get("tvmazeId"));

                        calendarData.get(day).add(event);
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Error fetching calendar in bldizi: " + e.getMessage());
        }

        return ResponseEntity.ok(calendarData);
    }
}
