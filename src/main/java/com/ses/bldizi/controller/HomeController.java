package com.ses.bldizi.controller;

import com.ses.bldizi.service.SystemSettingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.Map;

@Controller
public class HomeController {

    @Autowired
    private SystemSettingService systemSettingService;

    @GetMapping({"/", "/yakinda", "/coming-soon"})
    public ResponseEntity<Resource> getComingSoonPage() {
        return renderHtml("static/index.html");
    }

    @GetMapping("/maintenance")
    public ResponseEntity<?> getMaintenancePage() {
        if (!systemSettingService.isMaintenanceMode()) {
            return ResponseEntity.status(HttpStatus.FOUND)
                    .header(HttpHeaders.LOCATION, "/")
                    .build();
        }
        return renderHtml("static/maintenance.html");
    }

    @GetMapping("/about")
    public ResponseEntity<Resource> getAboutPage() {
        return renderHtml("static/about/html/about.html");
    }

    @GetMapping("/privacy_policy")
    public ResponseEntity<Resource> getPrivacyPolicyPage() {
        return renderHtml("static/privacy_policy/html/privacy_policy.html");
    }

    @GetMapping("/terms_of_use")
    public ResponseEntity<Resource> getTermsOfUsePage() {
        return renderHtml("static/terms_of_use/html/terms_of_use.html");
    }

    @GetMapping("/sss")
    public ResponseEntity<Resource> getFaqPage() {
        return renderHtml("static/sss/html/sss.html");
    }

    private ResponseEntity<Resource> renderHtml(String classpathLocation) {
        try {
            Resource htmlPage = new ClassPathResource(classpathLocation);
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
                "maintenance", systemSettingService.isMaintenanceMode(),
                "status", systemSettingService.isMaintenanceMode() ? "maintenance" : "online",
                "theme", "pink",
                "version", "1.0.0"
        );
    }
}
