package com.ses.bldizi.controller;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.Map;

@Controller
public class HomeController {

    @GetMapping({"/", "/yakinda", "/coming-soon"})
    public ResponseEntity<Resource> getComingSoonPage() {
        try {
            Resource htmlPage = new ClassPathResource("static/index.html");
            if (!htmlPage.exists()) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_HTML_VALUE + ";charset=UTF-8")
                    .body(htmlPage);
        } catch (Exception e) {
            return ResponseEntity.status(500).build();
        }
    }

    @GetMapping("/api/status")
    @ResponseBody
    public Map<String, Object> getStatus() {
        return Map.of(
                "site", "bldizi.com",
                "status", "coming_soon",
                "theme", "pink",
                "version", "1.0.0"
        );
    }
}
