package com.ses.bldizi.service;

import com.ses.bldizi.model.CustomPage;
import com.ses.bldizi.repository.CustomPageRepository;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Optional;

@Service
public class CustomPageService {

    private static final Logger logger = LoggerFactory.getLogger(CustomPageService.class);

    private final CustomPageRepository customPageRepository;

    @Value("${custom.pages.upload.dir:uploads/custom-pages}")
    private String uploadDirProperty;

    private Path baseUploadPath;

    public CustomPageService(CustomPageRepository customPageRepository) {
        this.customPageRepository = customPageRepository;
    }

    @PostConstruct
    public void init() {
        try {
            baseUploadPath = Paths.get(uploadDirProperty).toAbsolutePath().normalize();
            if (!Files.exists(baseUploadPath)) {
                Files.createDirectories(baseUploadPath);
            }
            logger.info("BLDizi CustomPage storage directory initialized at: {}", baseUploadPath);
        } catch (IOException e) {
            logger.error("Could not initialize CustomPage directory: {}", e.getMessage(), e);
        }
    }

    public Optional<CustomPage> findActiveBySlug(String slug) {
        if (slug == null || slug.isBlank()) return Optional.empty();
        return customPageRepository.findActiveBySlug(slug.trim());
    }

    public boolean servePageDirectly(String slug, String subPath, HttpServletRequest request, HttpServletResponse response) throws IOException {
        Optional<CustomPage> pageOpt = findActiveBySlug(slug);
        if (pageOpt.isEmpty()) {
            return false;
        }

        CustomPage page = pageOpt.get();
        String targetSite = page.getTargetSite() != null ? page.getTargetSite() : "whodatidols";


        String serverName = request != null ? request.getServerName() : "";
        boolean isLocalhost = serverName != null && (serverName.equalsIgnoreCase("localhost") || serverName.equals("127.0.0.1"));

        if ("whodatidols".equalsIgnoreCase(targetSite)) {
            String target;
            if (isLocalhost) {
                target = "http://localhost:8080/" + slug + (subPath != null ? subPath : "");
            } else {
                target = "https://whodatidols.com/" + slug + (subPath != null ? subPath : "");
            }
            response.setStatus(HttpServletResponse.SC_MOVED_TEMPORARILY);
            response.setHeader("Location", target);
            return true;
        }

        if (baseUploadPath == null) {
            return false;
        }

        Path targetDir = baseUploadPath.resolve(page.getSlug()).normalize();
        if (!Files.exists(targetDir) || !Files.isDirectory(targetDir)) {
            return false;
        }

        Path requestedFile;
        if (subPath == null || subPath.isBlank() || subPath.equals("/")) {
            requestedFile = targetDir.resolve(page.getEntryFile()).normalize();
        } else {
            String cleanSubPath = subPath.startsWith("/") ? subPath.substring(1) : subPath;
            requestedFile = targetDir.resolve(cleanSubPath).normalize();
        }

        if (!requestedFile.startsWith(targetDir)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return true;
        }

        if (Files.isDirectory(requestedFile)) {
            requestedFile = requestedFile.resolve("index.html").normalize();
        }

        if (!Files.exists(requestedFile) || !Files.isRegularFile(requestedFile)) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return true;
        }

        MediaType mediaType = determineMediaType(requestedFile.getFileName().toString());
        response.setContentType(mediaType.toString());
        response.setContentLengthLong(Files.size(requestedFile));
        response.setHeader("Cache-Control", mediaType.equals(MediaType.TEXT_HTML) ? "no-cache" : "public, max-age=86400");

        try (InputStream is = Files.newInputStream(requestedFile); OutputStream os = response.getOutputStream()) {
            is.transferTo(os);
            os.flush();
        }
        return true;
    }

    private MediaType determineMediaType(String fileName) {
        String lower = fileName.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".html") || lower.endsWith(".htm")) return MediaType.TEXT_HTML;
        if (lower.endsWith(".css")) return MediaType.valueOf("text/css");
        if (lower.endsWith(".js")) return MediaType.valueOf("application/javascript");
        if (lower.endsWith(".json")) return MediaType.APPLICATION_JSON;
        if (lower.endsWith(".png")) return MediaType.IMAGE_PNG;
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return MediaType.IMAGE_JPEG;
        if (lower.endsWith(".gif")) return MediaType.IMAGE_GIF;
        if (lower.endsWith(".webp")) return MediaType.valueOf("image/webp");
        if (lower.endsWith(".svg")) return MediaType.valueOf("image/svg+xml");
        if (lower.endsWith(".ico")) return MediaType.valueOf("image/x-icon");
        if (lower.endsWith(".mp4")) return MediaType.valueOf("video/mp4");
        if (lower.endsWith(".mp3")) return MediaType.valueOf("video/mpeg");
        if (lower.endsWith(".woff2")) return MediaType.valueOf("font/woff2");
        if (lower.endsWith(".woff")) return MediaType.valueOf("font/woff");
        if (lower.endsWith(".ttf")) return MediaType.valueOf("font/ttf");
        return MediaType.APPLICATION_OCTET_STREAM;
    }
}
