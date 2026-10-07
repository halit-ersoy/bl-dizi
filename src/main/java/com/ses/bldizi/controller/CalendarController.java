package com.ses.bldizi.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;

@RestController
public class CalendarController {

    private final JdbcTemplate jdbcTemplate;

    public CalendarController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/api/calendar")
    public ResponseEntity<Map<String, List<Map<String, Object>>>> getCalendar() {
        Map<String, List<Map<String, Object>>> calendarData = new LinkedHashMap<>();
        String[] days = { "monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday" };
        for (String day : days) {
            calendarData.put(day, new ArrayList<>());
        }

        try {
            String sql = "SELECT * FROM CalendarEvent ORDER BY sortOrder ASC, showTime ASC";
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql);

            for (Map<String, Object> row : rows) {
                Object dayObj = row.get("dayOfWeek");
                if (dayObj != null) {
                    String day = dayObj.toString().toLowerCase();
                    if (calendarData.containsKey(day)) {
                        Map<String, Object> event = new HashMap<>();
                        event.put("id", row.get("ID"));
                        event.put("title", row.get("title"));
                        event.put("episode", row.get("episode"));
                        event.put("time", row.get("showTime"));
                        calendarData.get(day).add(event);
                    }
                }
            }
        } catch (Exception ignored) {
        }

        return ResponseEntity.ok(calendarData);
    }
}
