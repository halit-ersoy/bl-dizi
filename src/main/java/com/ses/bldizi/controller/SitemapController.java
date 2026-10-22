package com.ses.bldizi.controller;

import com.ses.bldizi.util.SlugUtil;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

@RestController
@SuppressWarnings({"SqlResolve", "SqlNoDataSourceInspection", "ConstantConditions", "RedundantSuppression"})
public class SitemapController {

    private final JdbcTemplate jdbcTemplate;

    private static final String BASE_URL = "https://bldizi.com";
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd")
            .withZone(ZoneId.of("UTC"));

    private static final Set<String> BLACKLISTED_SLUGS = Set.of(
            "delete", "edit", "admin", "login", "register", "panel", "api", "logout", "error", "404", "watch",
            "messages", "settings", "profile", "favorites", "history", "login-admin", "banned"
    );

    public SitemapController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Main Sitemap Index (Dizin Dosyasi)
     * Google and search engines read this to discover all BL & GL sub-sitemaps.
     */
    @GetMapping(value = "/sitemap.xml", produces = MediaType.APPLICATION_XML_VALUE)
    @Cacheable(value = "sitemaps", key = "'index'")
    public String getSitemapIndex() {
        String today = LocalDate.now(ZoneId.of("UTC")).toString();
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<sitemapindex xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n");

        addIndexEntry(xml, "/sitemap-main.xml", today);
        addIndexEntry(xml, "/sitemap-movies.xml", today);
        addIndexEntry(xml, "/sitemap-series.xml", today);
        addIndexEntry(xml, "/sitemap-episodes.xml", today);
        addIndexEntry(xml, "/sitemap-upcoming.xml", today);

        xml.append("</sitemapindex>");
        return xml.toString();
    }

    /**
     * Static & Navigation Pages
     */
    @GetMapping(value = "/sitemap-main.xml", produces = MediaType.APPLICATION_XML_VALUE)
    @Cacheable(value = "sitemaps", key = "'main'")
    public String getMainSitemap() {
        StringBuilder xml = startUrlSet();
        Set<String> added = new HashSet<>();
        Instant now = Instant.now();

        addUrl(xml, added, "/", "1.0", "daily", now, null, null);
        addUrl(xml, added, "/filmler", "0.9", "daily", now, null, null);
        addUrl(xml, added, "/diziler", "0.9", "daily", now, null, null);
        addUrl(xml, added, "/coming-soon", "0.7", "weekly", now, null, null);
        addUrl(xml, added, "/about", "0.5", "monthly", null, null, null);
        addUrl(xml, added, "/sss", "0.5", "monthly", null, null, null);
        addUrl(xml, added, "/terms_of_use", "0.3", "monthly", null, null, null);
        addUrl(xml, added, "/privacy_policy", "0.3", "monthly", null, null, null);

        xml.append("</urlset>");
        return xml.toString();
    }

    /**
     * BL & GL Movies Sitemap (with Image extension)
     */
    @GetMapping(value = "/sitemap-movies.xml", produces = MediaType.APPLICATION_XML_VALUE)
    @Cacheable(value = "sitemaps", key = "'movies'")
    public String getMoviesSitemap() {
        StringBuilder xml = startUrlSet();
        Set<String> added = new HashSet<>();

        // language=none
        String sql = """
                SELECT m.ID, m.name, m.slug, m.uploadDate
                FROM Movie m
                WHERE (m.IsHidden = 0 OR m.IsHidden IS NULL)
                  AND (m.MovieType IN ('BL', 'GL') OR m.isBl = 1 OR EXISTS (
                        SELECT 1 FROM MovieCategories mc WHERE mc.MovieID = m.ID AND mc.CategoryID IN (52, 63)
                  ))
                ORDER BY m.uploadDate DESC
                """;

        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql);
            for (Map<String, Object> row : rows) {
                String name = (String) row.get("name");
                String slug = (String) row.get("slug");
                if (slug == null || slug.isBlank()) {
                    slug = row.get("ID").toString();
                }
                if (slug != null && !slug.isBlank() && !BLACKLISTED_SLUGS.contains(slug.toLowerCase(Locale.ROOT))) {
                    Object dt = row.get("uploadDate");
                    Instant lastmod = null;
                    if (dt instanceof java.sql.Timestamp ts) {
                        lastmod = ts.toInstant();
                    }
                    String imgUrl = BASE_URL + "/media/image/" + row.get("ID");
                    addUrl(xml, added, "/" + slug, "0.8", "weekly", lastmod, imgUrl, name);
                }
            }
        } catch (Exception e) {
            System.err.println("BL Movies sitemap error: " + e.getMessage());
        }

        xml.append("</urlset>");
        return xml.toString();
    }

    /**
     * BL & GL Series Sitemap (with Image extension)
     */
    @GetMapping(value = "/sitemap-series.xml", produces = MediaType.APPLICATION_XML_VALUE)
    @Cacheable(value = "sitemaps", key = "'series'")
    public String getSeriesSitemap() {
        StringBuilder xml = startUrlSet();
        Set<String> added = new HashSet<>();

        // language=none
        String sql = """
                SELECT s.ID, s.name, s.slug, s.uploadDate
                FROM Series s
                WHERE (s.IsHidden = 0 OR s.IsHidden IS NULL)
                  AND (s.SeriesType IN ('BL', 'GL') OR s.isBl = 1 OR EXISTS (
                        SELECT 1 FROM SeriesCategories sc WHERE sc.SeriesID = s.ID AND sc.CategoryID IN (52, 63)
                  ))
                ORDER BY s.uploadDate DESC
                """;

        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql);
            for (Map<String, Object> row : rows) {
                String name = (String) row.get("name");
                String slug = (String) row.get("slug");
                if (slug == null || slug.isBlank()) {
                    slug = row.get("ID").toString();
                }
                if (slug != null && !slug.isBlank() && !BLACKLISTED_SLUGS.contains(slug.toLowerCase(Locale.ROOT))) {
                    Object dt = row.get("uploadDate");
                    Instant lastmod = null;
                    if (dt instanceof java.sql.Timestamp ts) {
                        lastmod = ts.toInstant();
                    }
                    String imgUrl = BASE_URL + "/media/image/" + row.get("ID");
                    addUrl(xml, added, "/" + slug, "0.8", "weekly", lastmod, imgUrl, name);
                }
            }
        } catch (Exception e) {
            System.err.println("BL Series sitemap error: " + e.getMessage());
        }

        xml.append("</urlset>");
        return xml.toString();
    }

    /**
     * BL & GL Episodes Sitemap (with Image extension)
     */
    @GetMapping(value = "/sitemap-episodes.xml", produces = MediaType.APPLICATION_XML_VALUE)
    @Cacheable(value = "sitemaps", key = "'episodes'")
    public String getEpisodesSitemap() {
        StringBuilder xml = startUrlSet();
        Set<String> added = new HashSet<>();

        // language=none
        String sql = """
                SELECT e.ID, e.slug, e.SeasonNumber, e.EpisodeNumber, e.uploadDate, s.ID as seriesId, s.name as seriesName
                FROM Episode e
                JOIN Series s ON e.SeriesId = s.ID
                WHERE (e.IsHidden = 0 OR e.IsHidden IS NULL)
                  AND (s.IsHidden = 0 OR s.IsHidden IS NULL)
                  AND (s.SeriesType IN ('BL', 'GL') OR s.isBl = 1 OR EXISTS (
                        SELECT 1 FROM SeriesCategories sc WHERE sc.SeriesID = s.ID AND sc.CategoryID IN (52, 63)
                  ))
                ORDER BY e.uploadDate DESC
                """;

        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql);
            for (Map<String, Object> row : rows) {
                String slug = (String) row.get("slug");
                if (slug == null || slug.isBlank()) {
                    slug = row.get("ID").toString();
                }
                if (slug != null && !slug.isBlank() && !BLACKLISTED_SLUGS.contains(slug.toLowerCase(Locale.ROOT))) {
                    Object dt = row.get("uploadDate");
                    Instant lastmod = null;
                    if (dt instanceof java.sql.Timestamp ts) {
                        lastmod = ts.toInstant();
                    }
                    String seriesName = (String) row.get("seriesName");
                    Object season = row.get("SeasonNumber");
                    Object episode = row.get("EpisodeNumber");
                    String epTitle = (seriesName != null ? seriesName : "") + " " + season + ". Sezon " + episode + ". Bölüm";
                    String imgUrl = BASE_URL + "/media/image/" + row.get("seriesId");
                    addUrl(xml, added, "/" + slug, "0.7", "weekly", lastmod, imgUrl, epTitle);
                }
            }
        } catch (Exception e) {
            System.err.println("BL Episodes sitemap error: " + e.getMessage());
        }

        xml.append("</urlset>");
        return xml.toString();
    }

    /**
     * BL & GL Upcoming Shows / Movies Sitemap
     */
    @GetMapping(value = "/sitemap-upcoming.xml", produces = MediaType.APPLICATION_XML_VALUE)
    @Cacheable(value = "sitemaps", key = "'upcoming'")
    public String getUpcomingSitemap() {
        StringBuilder xml = startUrlSet();
        Set<String> added = new HashSet<>();

        try {
            // language=none
        String sql = """
                    SELECT u.ID as upcomingId, u.Name, COALESCE(u.Slug, s.slug, m.slug) as slug,
                           u.CreatedAt, u.[datetime]
                    FROM Upcoming u
                    LEFT JOIN Series s ON u.ReferenceId = s.ID
                    LEFT JOIN Movie m ON u.ReferenceId = m.ID
                    WHERE (u.isBl = 1 OR u.Genre IN ('BL', 'GL'))
                    ORDER BY u.[datetime]
                    """;
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql);
            for (Map<String, Object> row : rows) {
                String name = (String) row.get("Name");
                String slug = (String) row.get("slug");
                if (slug == null || slug.isBlank()) {
                    slug = SlugUtil.toSlug(name);
                }
                if (slug.isBlank()) {
                    slug = row.get("upcomingId").toString();
                }
                if (slug != null && !slug.isBlank() && !BLACKLISTED_SLUGS.contains(slug.toLowerCase(Locale.ROOT))) {
                    Object dt = row.get("CreatedAt");
                    Instant lastmod = null;
                    if (dt instanceof java.sql.Timestamp ts) {
                        lastmod = ts.toInstant();
                    }
                    String imgUrl = BASE_URL + "/media/image/" + row.get("upcomingId");
                    addUrl(xml, added, "/" + slug, "0.8", "daily", lastmod != null ? lastmod : Instant.now(), imgUrl, name);
                }
            }
        } catch (Exception e) {
            System.err.println("BL Upcoming sitemap error: " + e.getMessage());
        }

        xml.append("</urlset>");
        return xml.toString();
    }

    private StringBuilder startUrlSet() {
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\"\n");
        xml.append("        xmlns:image=\"http://www.google.com/schemas/sitemap-image/1.1\">\n");
        return xml;
    }

    private void addIndexEntry(StringBuilder xml, String path, String lastmod) {
        xml.append("  <sitemap>\n");
        xml.append("    <loc>").append(BASE_URL).append(path).append("</loc>\n");
        if (lastmod != null) {
            xml.append("    <lastmod>").append(lastmod).append("</lastmod>\n");
        }
        xml.append("  </sitemap>\n");
    }

    private void addUrl(StringBuilder xml, Set<String> addedUrls, String path, String priority,
                        String changefreq, Instant lastmod, String imageUrl, String imageTitle) {
        if (addedUrls.contains(path)) return;
        addedUrls.add(path);

        String fullUrl = escapeXml(BASE_URL + path);

        xml.append("  <url>\n");
        xml.append("    <loc>").append(fullUrl).append("</loc>\n");
        if (lastmod != null) {
            xml.append("    <lastmod>").append(FORMATTER.format(lastmod)).append("</lastmod>\n");
        }
        xml.append("    <changefreq>").append(changefreq).append("</changefreq>\n");
        xml.append("    <priority>").append(priority).append("</priority>\n");

        if (imageUrl != null && !imageUrl.isBlank()) {
            xml.append("    <image:image>\n");
            xml.append("      <image:loc>").append(escapeXml(imageUrl)).append("</image:loc>\n");
            if (imageTitle != null && !imageTitle.isBlank()) {
                xml.append("      <image:title>").append(escapeXml(imageTitle)).append("</image:title>\n");
            }
            xml.append("    </image:image>\n");
        }

        xml.append("  </url>\n");
    }

    private String escapeXml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}
