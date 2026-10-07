package com.ses.bldizi.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/media")
public class MediaController {

    private static final Logger logger = LoggerFactory.getLogger(MediaController.class);
    private static final String[] EXTENSIONS = { ".jpg", ".jpeg", ".png", ".webp" };

    private static final String FALLBACK_SVG = """
        <svg xmlns="http://www.w3.org/2000/svg" width="300" height="450" viewBox="0 0 300 450">
          <defs>
            <linearGradient id="g" x1="0%" y1="0%" x2="100%" y2="100%">
              <stop offset="0%" stop-color="#1e1e24"/>
              <stop offset="100%" stop-color="#121216"/>
            </linearGradient>
          </defs>
          <rect width="100%" height="100%" fill="url(#g)"/>
          <circle cx="150" cy="195" r="42" fill="#ff4081" fill-opacity="0.12" stroke="#ff4081" stroke-width="2"/>
          <polygon points="143,182 165,195 143,208" fill="#ff4081"/>
          <text x="150" y="270" font-family="'Montserrat', sans-serif" font-size="18" font-weight="700" fill="#ffffff" text-anchor="middle" letter-spacing="3">BL DİZİ</text>
        </svg>
        """;

    @Value("${media.source.movies.path}")
    private String moviesPath;

    @Value("${media.source.soap_operas.path}")
    private String soapOperasPath;

    @Value("${media.static.images.path}")
    private String staticImagesPath;

    @Value("${media.profile.images.path:D:\\SourceFiles\\mssql\\media\\images\\profile}")
    private String profileImagesPath;

    private final JdbcTemplate jdbcTemplate;

    public MediaController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/image/{id}")
    public ResponseEntity<Resource> getImage(@PathVariable("id") UUID id) {
        try {
            // 1. Try finding in soap-operas
            Path imagePath = findExistingImage(Paths.get(soapOperasPath), id);

            // 2. Try finding in movies
            if (imagePath == null) {
                imagePath = findExistingImage(Paths.get(moviesPath), id);
            }

            // 3. Fallback: if it's an episode ID, lookup parent series ID
            if (imagePath == null) {
                try {
                    String parentSeriesId = jdbcTemplate.queryForObject(
                            "SELECT SeriesID FROM Episode WHERE ID = ?", String.class, id.toString());
                    if (parentSeriesId != null) {
                        UUID pId = UUID.fromString(parentSeriesId);
                        imagePath = findExistingImage(Paths.get(soapOperasPath), pId);
                    }
                } catch (Exception ignored) {
                }
            }

            // 4. Try static images
            if (imagePath == null) {
                imagePath = findExistingImage(Paths.get(staticImagesPath), id);
            }

            if (imagePath != null && Files.exists(imagePath)) {
                MediaType mediaType = determineMediaType(imagePath);
                long lastModified = Files.getLastModifiedTime(imagePath).toMillis();

                return ResponseEntity.ok()
                        .contentType(mediaType)
                        .lastModified(lastModified)
                        .cacheControl(CacheControl.maxAge(7, TimeUnit.DAYS).cachePublic())
                        .body(new UrlResource(imagePath.toUri()));
            }

            // Default placeholder SVG if physical file does not exist on disk
            byte[] svgBytes = FALLBACK_SVG.getBytes(StandardCharsets.UTF_8);
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType("image/svg+xml"))
                    .cacheControl(CacheControl.maxAge(1, TimeUnit.DAYS).cachePublic())
                    .body(new ByteArrayResource(svgBytes));

        } catch (IOException e) {
            logger.error("Error serving image {}: {}", id, e.getMessage());
            byte[] svgBytes = FALLBACK_SVG.getBytes(StandardCharsets.UTF_8);
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType("image/svg+xml"))
                    .body(new ByteArrayResource(svgBytes));
        }
    }

    @GetMapping("/profile/{id}")
    public ResponseEntity<Resource> getProfileImage(@PathVariable("id") UUID id) {
        try {
            Path rootPath = Paths.get(profileImagesPath).toAbsolutePath().normalize();
            Path imagePath = findExistingImage(rootPath, id);

            if (imagePath == null || !Files.exists(imagePath)) {
                return ResponseEntity.notFound().build();
            }

            MediaType mediaType = determineMediaType(imagePath);
            long lastModified = Files.getLastModifiedTime(imagePath).toMillis();

            return ResponseEntity.ok()
                    .contentType(mediaType)
                    .lastModified(lastModified)
                    .cacheControl(CacheControl.noCache().mustRevalidate())
                    .body(new UrlResource(imagePath.toUri()));
        } catch (IOException e) {
            logger.error("Error serving profile image {}: {}", id, e.getMessage());
            return ResponseEntity.notFound().build();
        }
    }

    private Path findExistingImage(Path basePath, UUID id) {
        if (!Files.exists(basePath)) return null;

        String idStrLower = id.toString().toLowerCase();
        String idStrUpper = id.toString().toUpperCase();

        for (String ext : EXTENSIONS) {
            Path candidate = basePath.resolve(idStrLower + ext);
            if (Files.exists(candidate)) return candidate;

            candidate = basePath.resolve(idStrUpper + ext);
            if (Files.exists(candidate)) return candidate;
        }
        return null;
    }

    private MediaType determineMediaType(Path path) {
        String fileName = path.getFileName().toString().toLowerCase();
        if (fileName.endsWith(".png")) return MediaType.IMAGE_PNG;
        if (fileName.endsWith(".webp")) return MediaType.parseMediaType("image/webp");
        return MediaType.IMAGE_JPEG;
    }
}
