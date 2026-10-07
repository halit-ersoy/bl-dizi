package com.ses.bldizi.controller;

import com.ses.bldizi.model.VideoSource;
import com.ses.bldizi.repository.VideoSourceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.core.io.support.ResourceRegion;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/media")
public class MediaController {

    private static final Logger logger = LoggerFactory.getLogger(MediaController.class);
    private static final String[] EXTENSIONS = { ".jpg", ".jpeg", ".png", ".webp" };
    private static final long VIDEO_CHUNK_SIZE = 1024 * 1024 * 2; // 2MB

    @Value("${media.source.movies.path}")
    private String moviesPath;

    @Value("${media.source.soap_operas.path}")
    private String soapOperasPath;

    @Value("${media.static.images.path}")
    private String staticImagesPath;

    @Value("${media.profile.images.path:D:\\SourceFiles\\mssql\\media\\images\\profile}")
    private String profileImagesPath;

    @Value("${media.actor.images.path:D:\\SourceFiles\\mssql\\media\\images\\actors}")
    private String actorImagesPath;

    private final JdbcTemplate jdbcTemplate;
    private final VideoSourceRepository videoSourceRepository;

    public MediaController(JdbcTemplate jdbcTemplate, VideoSourceRepository videoSourceRepository) {
        this.jdbcTemplate = jdbcTemplate;
        this.videoSourceRepository = videoSourceRepository;
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
                        .cacheControl(CacheControl.noCache().mustRevalidate())
                        .body(new UrlResource(imagePath.toUri()));
            }

            return ResponseEntity.notFound().cacheControl(CacheControl.noCache().mustRevalidate()).build();

        } catch (IOException e) {
            logger.error("Error serving image {}: {}", id, e.getMessage());
            return ResponseEntity.notFound().cacheControl(CacheControl.noCache().mustRevalidate()).build();
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

    @GetMapping("/actor/{id}")
    public ResponseEntity<Resource> getActorImage(@PathVariable("id") UUID id) {
        try {
            Path rootPath = Paths.get(actorImagesPath).toAbsolutePath().normalize();
            Path imagePath = findExistingImage(rootPath, id);

            if (imagePath == null || !Files.exists(imagePath)) {
                return ResponseEntity.notFound().build();
            }

            MediaType mediaType = determineMediaType(imagePath);
            long lastModified = Files.getLastModifiedTime(imagePath).toMillis();

            return ResponseEntity.ok()
                    .contentType(mediaType)
                    .lastModified(lastModified)
                    .cacheControl(CacheControl.maxAge(7, TimeUnit.DAYS).cachePublic())
                    .body(new UrlResource(imagePath.toUri()));
        } catch (IOException e) {
            logger.error("Error serving actor image {}: {}", id, e.getMessage());
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/video/{id}")
    public ResponseEntity<ResourceRegion> getVideo(
            @PathVariable(name = "id") UUID id,
            @RequestHeader HttpHeaders headers) {

        try {
            String category = getContentTypeFromDatabase(id);
            Path basePath = getBasePathForCategory(category);

            if (basePath == null) {
                logger.warn("Unknown category '{}' for video id: {}", category, id);
                return ResponseEntity.notFound().build();
            }

            Path videoPath = basePath.resolve(id + ".mp4").normalize().toAbsolutePath();

            if (!Files.exists(videoPath) && "soap_opera".equalsIgnoreCase(category)) {
                UUID episodeId = findFirstEpisodeIdForSeries(id);
                if (episodeId != null) {
                    id = episodeId;
                    videoPath = basePath.resolve(id + ".mp4").normalize().toAbsolutePath();
                }
            }

            if (!Files.exists(videoPath)) {
                return ResponseEntity.notFound().build();
            }

            UrlResource videoResource = new UrlResource(videoPath.toUri());
            long contentLength = videoResource.contentLength();
            ResourceRegion region = getResourceRegion(videoResource, headers, contentLength);

            MediaType mediaType = MediaTypeFactory.getMediaType(videoResource)
                    .orElse(MediaType.APPLICATION_OCTET_STREAM);

            return ResponseEntity
                    .status(HttpStatus.PARTIAL_CONTENT)
                    .contentType(mediaType)
                    .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                    .body(region);

        } catch (Exception e) {
            logger.error("Error serving video for id {}: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/video/{id}/sources")
    public ResponseEntity<List<VideoSource>> getSources(@PathVariable("id") UUID id) {
        try {
            List<VideoSource> sources = videoSourceRepository.findByContentId(id);
            if (sources == null) return ResponseEntity.ok(List.of());
            return ResponseEntity.ok(sources);
        } catch (Exception e) {
            logger.error("Error retrieving video sources for id {}: {}", id, e.getMessage());
            return ResponseEntity.ok(List.of());
        }
    }

    @GetMapping("/video/{id}/playlist.m3u8")
    public ResponseEntity<Resource> getHlsPlaylist(@PathVariable("id") UUID id) {
        try {
            String category = getContentTypeFromDatabase(id);
            Path basePath = getBasePathForCategory(category);
            if (basePath == null) return ResponseEntity.notFound().build();

            Path hlsPath = basePath.resolve("hls").resolve(id.toString()).resolve("playlist.m3u8");
            if (!Files.exists(hlsPath)) {
                return ResponseEntity.notFound().build();
            }

            Resource resource = new UrlResource(hlsPath.toUri());
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType("application/vnd.apple.mpegurl"))
                    .body(resource);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/video/{id}/{segment}.ts")
    public ResponseEntity<Resource> getHlsSegment(
            @PathVariable("id") UUID id,
            @PathVariable("segment") String segment) {
        try {
            String category = getContentTypeFromDatabase(id);
            Path basePath = getBasePathForCategory(category);
            if (basePath == null) return ResponseEntity.notFound().build();

            Path segmentPath = basePath.resolve("hls").resolve(id.toString()).resolve(segment + ".ts");
            if (!Files.exists(segmentPath)) {
                return ResponseEntity.notFound().build();
            }

            Resource resource = new UrlResource(segmentPath.toUri());
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType("video/MP2T"))
                    .body(resource);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    private Path getBasePathForCategory(String category) {
        if (category == null) return null;
        return switch (category.toLowerCase()) {
            case "movie" -> Paths.get(moviesPath).toAbsolutePath().normalize();
            case "soap_opera", "soap-opera", "soapopera", "series", "episode" -> Paths.get(soapOperasPath).toAbsolutePath().normalize();
            default -> null;
        };
    }

    private String getContentTypeFromDatabase(UUID id) {
        if (id == null) return "unknown";
        try {
            Integer countMovie = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM Movie WHERE ID = ?", Integer.class, id.toString());
            if (countMovie != null && countMovie > 0) return "movie";

            Integer countSeries = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM Series WHERE ID = ?", Integer.class, id.toString());
            if (countSeries != null && countSeries > 0) return "soap_opera";

            Integer countEp = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM Episode WHERE ID = ?", Integer.class, id.toString());
            if (countEp != null && countEp > 0) return "soap_opera";
        } catch (Exception ignored) {}
        return "unknown";
    }

    private UUID findFirstEpisodeIdForSeries(UUID seriesId) {
        try {
            String sql = "SELECT TOP 1 ID FROM Episode WHERE SeriesId = ? ORDER BY SeasonNumber ASC, EpisodeNumber ASC";
            String idStr = jdbcTemplate.queryForObject(sql, String.class, seriesId.toString());
            return idStr != null ? UUID.fromString(idStr) : null;
        } catch (Exception e) {
            return null;
        }
    }

    private ResourceRegion getResourceRegion(UrlResource resource, HttpHeaders headers, long contentLength) {
        String range = headers.getFirst(HttpHeaders.RANGE);
        if (range == null || range.isEmpty()) {
            return new ResourceRegion(resource, 0, Math.min(VIDEO_CHUNK_SIZE, contentLength));
        }

        try {
            String[] ranges = range.replace("bytes=", "").split("-");
            long start = Long.parseLong(ranges[0]);
            if (start >= contentLength) {
                return new ResourceRegion(resource, 0, Math.min(VIDEO_CHUNK_SIZE, contentLength));
            }

            long end = ranges.length > 1 && !ranges[1].isEmpty()
                    ? Math.min(Long.parseLong(ranges[1]), contentLength - 1)
                    : contentLength - 1;

            return new ResourceRegion(resource, start, Math.min(VIDEO_CHUNK_SIZE, end - start + 1));
        } catch (NumberFormatException e) {
            return new ResourceRegion(resource, 0, Math.min(VIDEO_CHUNK_SIZE, contentLength));
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
