package com.ses.bldizi.repository;

import com.ses.bldizi.model.FeaturedTvItemDto;
import com.ses.bldizi.model.VideoViewModel;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.*;

@Repository
public class SeriesRepository {

    private final JdbcTemplate jdbcTemplate;

    public SeriesRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<VideoViewModel> findTop6BlSeriesByViews() {
        String sql = """
            SELECT TOP 6 s.ID, s.name, s.Summary, s.Country, s.Language, s.slug, s.viewCount,
                   (SELECT STRING_AGG(c.Name, ', ') FROM Categories c 
                    JOIN SeriesCategories sc2 ON sc2.CategoryID = c.ID 
                    WHERE sc2.SeriesID = s.ID) as Category
            FROM Series s
            JOIN SeriesCategories sc ON s.ID = sc.SeriesID
            WHERE sc.CategoryID IN (52, 63)
              AND (s.IsHidden = 0 OR s.IsHidden IS NULL)
            GROUP BY s.ID, s.name, s.Summary, s.Country, s.Language, s.slug, s.viewCount
            ORDER BY s.viewCount DESC
        """;

        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            String idStr = rs.getString("ID");
            UUID seriesId = UUID.fromString(idStr);
            String name = rs.getString("name");
            String category = rs.getString("Category");
            String country = rs.getString("Country");
            String slug = rs.getString("slug");

            String mainCategory = category != null && category.contains(",") ? category.split(",")[0].trim() : (category != null ? category : "BL Dizi");
            String videoUrl = (slug != null && !slug.isEmpty()) ? "/" + slug : "/" + seriesId;

            return VideoViewModel.builder()
                    .id(seriesId.toString())
                    .title(name)
                    .info(mainCategory)
                    .thumbnailUrl("/media/image/" + seriesId)
                    .videoUrl(videoUrl)
                    .country(country != null ? country.toLowerCase() : "th")
                    .build();
        });
    }

    public List<VideoViewModel> findRecentSeriesPaged(int offset, int limit) {
        String sql = """
            SELECT s.ID, s.name, s.Country, s.slug,
                   (SELECT STRING_AGG(c.Name, ', ') FROM Categories c 
                    JOIN SeriesCategories sc2 ON sc2.CategoryID = c.ID 
                    WHERE sc2.SeriesID = s.ID) as Category
            FROM Series s
            JOIN SeriesCategories sc ON s.ID = sc.SeriesID
            WHERE sc.CategoryID IN (52, 63)
              AND (s.IsHidden = 0 OR s.IsHidden IS NULL)
            GROUP BY s.ID, s.name, s.Country, s.slug, s.uploadDate
            ORDER BY s.uploadDate DESC
            OFFSET ? ROWS FETCH NEXT ? ROWS ONLY
        """;

        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            String idStr = rs.getString("ID");
            UUID seriesId = UUID.fromString(idStr);
            String name = rs.getString("name");
            String category = rs.getString("Category");
            String country = rs.getString("Country");
            String slug = rs.getString("slug");

            String mainCategory = category != null && category.contains(",") ? category.split(",")[0].trim() : (category != null ? category : "BL Dizi");
            String videoUrl = (slug != null && !slug.isEmpty()) ? "/" + slug : "/" + seriesId;

            return VideoViewModel.builder()
                    .id(seriesId.toString())
                    .title(name)
                    .info(mainCategory)
                    .thumbnailUrl("/media/image/" + seriesId)
                    .videoUrl(videoUrl)
                    .country(country != null ? country.toLowerCase() : "th")
                    .build();
        }, offset, limit);
    }

    public int countAllSeries() {
        String sql = """
            SELECT COUNT(DISTINCT s.ID)
            FROM Series s
            JOIN SeriesCategories sc ON s.ID = sc.SeriesID
            WHERE sc.CategoryID IN (52, 63)
              AND (s.IsHidden = 0 OR s.IsHidden IS NULL)
        """;
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class);
        return count != null ? count : 0;
    }

    public List<VideoViewModel> searchSeriesPaged(String query, int offset, int limit) {
        String sql = """
            SELECT s.ID, s.name, s.Country, s.slug,
                   (SELECT STRING_AGG(c.Name, ', ') FROM Categories c 
                    JOIN SeriesCategories sc2 ON sc2.CategoryID = c.ID 
                    WHERE sc2.SeriesID = s.ID) as Category
            FROM Series s
            JOIN SeriesCategories sc ON s.ID = sc.SeriesID
            WHERE sc.CategoryID IN (52, 63)
              AND (s.IsHidden = 0 OR s.IsHidden IS NULL)
              AND s.name LIKE ?
            GROUP BY s.ID, s.name, s.Country, s.slug, s.uploadDate
            ORDER BY s.uploadDate DESC
            OFFSET ? ROWS FETCH NEXT ? ROWS ONLY
        """;
        String wild = "%" + query.trim() + "%";
        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            String idStr = rs.getString("ID");
            UUID seriesId = UUID.fromString(idStr);
            String name = rs.getString("name");
            String category = rs.getString("Category");
            String country = rs.getString("Country");
            String slug = rs.getString("slug");

            String mainCategory = category != null && category.contains(",") ? category.split(",")[0].trim() : (category != null ? category : "BL Dizi");
            String videoUrl = (slug != null && !slug.isEmpty()) ? "/" + slug : "/" + seriesId;

            return VideoViewModel.builder()
                    .id(seriesId.toString())
                    .title(name)
                    .info(mainCategory)
                    .thumbnailUrl("/media/image/" + seriesId)
                    .videoUrl(videoUrl)
                    .country(country != null ? country.toLowerCase() : "th")
                    .build();
        }, wild, offset, limit);
    }

    public int countSeriesBySearch(String query) {
        String sql = """
            SELECT COUNT(DISTINCT s.ID)
            FROM Series s
            JOIN SeriesCategories sc ON s.ID = sc.SeriesID
            WHERE sc.CategoryID IN (52, 63)
              AND (s.IsHidden = 0 OR s.IsHidden IS NULL)
              AND s.name LIKE ?
        """;
        String wild = "%" + query.trim() + "%";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, wild);
        return count != null ? count : 0;
    }

    public List<FeaturedTvItemDto> findRecentBlEpisodes(int offset, int limit) {
        String sql = """
            WITH OrderedEpisodes AS (
                SELECT E.ID as EpisodeId, E.SeriesId as SeriesId, E.uploadDate, E.name, E.slug, E.SeasonNumber, E.EpisodeNumber,
                       S.name AS SeriesName, S.Language, S.Country, S.SeriesType, S.finalStatus,
                       ROW_NUMBER() OVER (PARTITION BY E.SeriesId ORDER BY E.uploadDate DESC, E.SeasonNumber DESC, E.EpisodeNumber DESC) as rn
                FROM Episode E
                JOIN Series S ON E.SeriesId = S.ID
                JOIN SeriesCategories SC ON S.ID = SC.SeriesID
                WHERE SC.CategoryID IN (52, 63)
                  AND (E.IsHidden = 0 OR E.IsHidden IS NULL)
                  AND (S.IsHidden = 0 OR S.IsHidden IS NULL)
            )
            SELECT EpisodeId, SeriesId, uploadDate, name, slug, SeasonNumber, EpisodeNumber, SeriesName, Language, Country, SeriesType, finalStatus
            FROM OrderedEpisodes
            WHERE rn = 1
            ORDER BY uploadDate DESC
            OFFSET ? ROWS FETCH NEXT ? ROWS ONLY
        """;

        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            UUID epId = UUID.fromString(rs.getString("EpisodeId"));
            UUID sId = UUID.fromString(rs.getString("SeriesId"));
            String sName = rs.getString("SeriesName");
            int season = rs.getInt("SeasonNumber");
            int episode = rs.getInt("EpisodeNumber");
            String slug = rs.getString("slug");
            String country = rs.getString("Country");
            String lang = rs.getString("Language");
            int finalStatus = rs.getInt("finalStatus");
            String seriesType = rs.getString("SeriesType");
            java.sql.Timestamp uploadDate = rs.getTimestamp("uploadDate");

            boolean isNew = uploadDate != null && 
                    (System.currentTimeMillis() - uploadDate.getTime()) < (7L * 24 * 60 * 60 * 1000);

            return FeaturedTvItemDto.builder()
                    .id(epId)
                    .seriesId(sId)
                    .title(sName)
                    .season(season)
                    .episode(episode)
                    .slug(slug != null && !slug.isEmpty() ? slug : epId.toString())
                    .image("/media/image/" + sId)
                    .country(country != null && !country.isEmpty() ? country.toLowerCase() : mapLangToCode(lang))
                    .language(lang)
                    .finalStatus(finalStatus)
                    .seriesType(seriesType)
                    .isNew(isNew)
                    .isFinal(finalStatus == 1)
                    .build();
        }, offset, limit);
    }

    public int countRecentBlEpisodes() {
        String sql = """
            WITH DistinctSeriesWithEpisode AS (
                SELECT DISTINCT E.SeriesId
                FROM Episode E
                JOIN Series S ON E.SeriesId = S.ID
                JOIN SeriesCategories SC ON S.ID = SC.SeriesID
                WHERE SC.CategoryID IN (52, 63)
                  AND (E.IsHidden = 0 OR E.IsHidden IS NULL)
                  AND (S.IsHidden = 0 OR S.IsHidden IS NULL)
            )
            SELECT COUNT(*) FROM DistinctSeriesWithEpisode
        """;
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class);
        return count != null ? count : 0;
    }

    public List<Map<String, Object>> searchBlContent(String query) {
        String sql = """
            SELECT TOP 8 s.ID, s.name, s.Country, s.slug, 'SoapOpera' as type,
                   (SELECT STRING_AGG(c.Name, ', ') FROM Categories c 
                    JOIN SeriesCategories sc2 ON sc2.CategoryID = c.ID 
                    WHERE sc2.SeriesID = s.ID) as Category
            FROM Series s
            JOIN SeriesCategories sc ON s.ID = sc.SeriesID
            WHERE sc.CategoryID IN (52, 63)
              AND (s.IsHidden = 0 OR s.IsHidden IS NULL)
              AND s.name LIKE ?
            GROUP BY s.ID, s.name, s.Country, s.slug
            UNION
            SELECT TOP 4 m.ID, m.name, m.Country, m.slug, 'Movie' as type,
                   (SELECT STRING_AGG(c.Name, ', ') FROM Categories c 
                    JOIN MovieCategories mc2 ON mc2.CategoryID = c.ID 
                    WHERE mc2.MovieID = m.ID) as Category
            FROM Movie m
            JOIN MovieCategories mc ON m.ID = mc.MovieID
            WHERE mc.CategoryID IN (52, 63)
              AND (m.IsHidden = 0 OR m.IsHidden IS NULL)
              AND m.name LIKE ?
        """;
        String wild = "%" + query.trim() + "%";
        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", rs.getString("ID"));
            map.put("name", rs.getString("name"));
            map.put("country", rs.getString("Country"));
            map.put("slug", rs.getString("slug"));
            map.put("type", rs.getString("type"));
            map.put("category", rs.getString("Category"));
            map.put("thumbnailUrl", "/media/image/" + rs.getString("ID"));
            return map;
        }, wild, wild);
    }

    private static String mapLangToCode(String lang) {
        if (lang == null) return "th";
        lang = lang.toLowerCase();
        if (lang.contains("kore") || lang.contains("korea")) return "kr";
        if (lang.contains("japon") || lang.contains("japan")) return "jp";
        if (lang.contains("çin") || lang.contains("china")) return "cn";
        if (lang.contains("tayland") || lang.contains("thai")) return "th";
        if (lang.contains("tayvan") || lang.contains("taiwan")) return "tw";
        if (lang.contains("filipin") || lang.contains("phili")) return "ph";
        return "th";
    }
}
