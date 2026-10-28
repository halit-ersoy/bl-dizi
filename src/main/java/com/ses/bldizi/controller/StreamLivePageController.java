package com.ses.bldizi.controller;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
public class StreamLivePageController {

    @GetMapping("/streamlive")
    public ResponseEntity<Resource> getStreamLivePage() {
        ClassPathResource htmlPage = new ClassPathResource("static/streamlive/html/streamlive.html");
        if (!htmlPage.exists()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_HTML_VALUE)
                .body(htmlPage);
    }

    @GetMapping("/streamlive/{id:[a-fA-F0-9\\-]+}")
    public ResponseEntity<Void> getStreamLivePageWithId(@PathVariable("id") String id) {
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, "/streamlive")
                .build();
    }
}
