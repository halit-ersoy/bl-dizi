package com.ses.bldizi.repository;

import com.ses.bldizi.model.EpisodeViewModel;
import com.ses.bldizi.model.FeaturedTvItemDto;
import com.ses.bldizi.model.VideoViewModel;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Repository
@SuppressWarnings({"SqlResolve", "SqlNoDataSourceInspection", "DuplicatedCode", "unused", "ConstantConditions"})
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
            LEFT JOIN (
                SELECT SeriesId, COUNT(*) as WeeklyViews
                FROM (
                    SELECT E.SeriesId
                    FROM Episode E
                    JOIN [dbo].[ContentViewLog] VL ON E.ID = VL.ContentId
                    WHERE VL.ViewedAt >= DATEADD(day, -7, GETDATE())
                    UNION ALL
                    SELECT VL.ContentId as SeriesId
                    FROM [dbo].[ContentViewLog] VL
                    WHERE VL.ViewedAt >= DATEADD(day, -7, GETDATE())
                      AND EXISTS (SELECT 1 FROM Series WHERE ID = VL.ContentId)
                ) Combined
                GROUP BY SeriesId
            ) V ON s.ID = V.SeriesId
            WHERE (s.IsHidden = 0 OR s.IsHidden IS NULL)
              AND (s.SeriesType IN ('BL', 'GL') OR s.isBl = 1 OR EXISTS (SELECT 1 FROM SeriesCategories sc WHERE sc.SeriesID = s.ID AND sc.CategoryID IN (52, 63)))
            ORDER BY ISNULL(V.WeeklyViews, 0) DESC, s.viewCount DESC, s.name ASC
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
                    .thumbnailUrl("/media/image/" + seriesId + "?v=2")
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
            WHERE (s.SeriesType IN ('BL', 'GL') OR s.isBl = 1 OR EXISTS (SELECT 1 FROM SeriesCategories sc WHERE sc.SeriesID = s.ID AND sc.CategoryID IN (52, 63)))
              AND (s.IsHidden = 0 OR s.IsHidden IS NULL)
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
                    .thumbnailUrl("/media/image/" + seriesId + "?v=2")
                    .videoUrl(videoUrl)
                    .country(country != null ? country.toLowerCase() : "th")
                    .build();
        }, offset, limit);
    }

    public int countAllSeries() {
        String sql = """
            SELECT COUNT(*)
            FROM Series s
            WHERE (s.SeriesType IN ('BL', 'GL') OR s.isBl = 1 OR EXISTS (SELECT 1 FROM SeriesCategories sc WHERE sc.SeriesID = s.ID AND sc.CategoryID IN (52, 63)))
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
            WHERE (s.SeriesType IN ('BL', 'GL') OR s.isBl = 1 OR EXISTS (SELECT 1 FROM SeriesCategories sc WHERE sc.SeriesID = s.ID AND sc.CategoryID IN (52, 63)))
              AND (s.IsHidden = 0 OR s.IsHidden IS NULL)
              AND s.name LIKE ?
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
                    .thumbnailUrl("/media/image/" + seriesId + "?v=2")
                    .videoUrl(videoUrl)
                    .country(country != null ? country.toLowerCase() : "th")
                    .build();
        }, wild, offset, limit);
    }

    public int countSeriesBySearch(String query) {
        String sql = """
            SELECT COUNT(*)
            FROM Series s
            WHERE (s.SeriesType IN ('BL', 'GL') OR s.isBl = 1 OR EXISTS (SELECT 1 FROM SeriesCategories sc WHERE sc.SeriesID = s.ID AND sc.CategoryID IN (52, 63)))
              AND (s.IsHidden = 0 OR s.IsHidden IS NULL)
              AND s.name LIKE ?
        """;
        String wild = "%" + query.trim() + "%";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, wild);
        return count != null ? count : 0;
    }

    public List<VideoViewModel> findSeriesWithFilters(String subType, Integer categoryId, Integer finalStatus,
                                                      Integer year, String country, String sort, int offset, int limit) {
        StringBuilder sql = new StringBuilder("""
            SELECT s.ID, s.name, s.Summary, s.Language, s.Country, s.finalStatus, s.uploadDate, s.slug,
                   (SELECT COUNT(*) FROM Episode e WHERE e.SeriesId = s.ID AND (e.IsHidden = 0 OR e.IsHidden IS NULL)) as episodeCount,
                   (SELECT STRING_AGG(c.Name, ', ') FROM Categories c 
                    JOIN SeriesCategories sc2 ON sc2.CategoryID = c.ID 
                    WHERE sc2.SeriesID = s.ID) as Category
            FROM Series s
            WHERE (s.IsHidden = 0 OR s.IsHidden IS NULL)
              AND (s.SeriesType IN ('BL', 'GL') OR s.isBl = 1 OR EXISTS (SELECT 1 FROM SeriesCategories sc_all WHERE sc_all.SeriesID = s.ID AND sc_all.CategoryID IN (52, 63)))
        """);

        List<Object> params = new ArrayList<>();

        if ("gl".equalsIgnoreCase(subType)) {
            sql.append(" AND (s.SeriesType = 'GL' OR EXISTS (SELECT 1 FROM SeriesCategories sc_gl WHERE sc_gl.SeriesID = s.ID AND sc_gl.CategoryID = 63))");
        } else if ("bl".equalsIgnoreCase(subType)) {
            sql.append(" AND (s.SeriesType = 'BL' OR (s.isBl = 1 AND (s.SeriesType != 'GL' OR s.SeriesType IS NULL)) OR EXISTS (SELECT 1 FROM SeriesCategories sc_bl WHERE sc_bl.SeriesID = s.ID AND sc_bl.CategoryID = 52)) AND NOT EXISTS (SELECT 1 FROM SeriesCategories sc_gl2 WHERE sc_gl2.SeriesID = s.ID AND sc_gl2.CategoryID = 63) AND (s.SeriesType != 'GL' OR s.SeriesType IS NULL)");
        }

        if (categoryId != null && categoryId > 0) {
            sql.append(" AND EXISTS (SELECT 1 FROM SeriesCategories sc_cat WHERE sc_cat.SeriesID = s.ID AND sc_cat.CategoryID = ?)");
            params.add(categoryId);
        }

        if (finalStatus != null && finalStatus >= 0) {
            sql.append(" AND s.finalStatus = ?");
            params.add(finalStatus);
        }

        if (year != null && year > 0) {
            sql.append(" AND (EXISTS (SELECT 1 FROM Episode e WHERE e.SeriesId = s.ID AND e.ReleaseYear = ?) OR YEAR(s.uploadDate) = ?)");
            params.add(year);
            params.add(year);
        }

        if (country != null && !country.isEmpty() && !"all".equalsIgnoreCase(country)) {
            sql.append(" AND LOWER(s.Country) = LOWER(?)");
            params.add(country.trim());
        }

        if ("oldest".equalsIgnoreCase(sort)) {
            sql.append(" ORDER BY s.uploadDate ASC, s.name ASC");
        } else if ("a-z".equalsIgnoreCase(sort)) {
            sql.append(" ORDER BY s.name ASC");
        } else if ("z-a".equalsIgnoreCase(sort)) {
            sql.append(" ORDER BY s.name DESC");
        } else {
            sql.append(" ORDER BY s.uploadDate DESC, s.name ASC");
        }

        sql.append(" OFFSET ? ROWS FETCH NEXT ? ROWS ONLY");
        params.add(offset);
        params.add(limit);

        return jdbcTemplate.query(sql.toString(), (rs, rowNum) -> {
            String idStr = rs.getString("ID");
            UUID seriesId = UUID.fromString(idStr);
            String name = rs.getString("name");
            String category = rs.getString("Category");
            String countryVal = rs.getString("Country");
            String slug = rs.getString("slug");
            int epCount = rs.getInt("episodeCount");
            int fStatus = rs.getInt("finalStatus");

            String mainCategory = category != null && category.contains(",") ? category.split(",")[0].trim() : (category != null ? category : "BL Dizi");
            String statusText = (fStatus == 1) ? "Tamamlandı" : "Devam Ediyor";
            String infoText = mainCategory + (epCount > 0 ? " • " + epCount + " Bölüm" : " • " + statusText);
            String videoUrl = (slug != null && !slug.isEmpty()) ? "/" + slug : "/" + seriesId;

            return VideoViewModel.builder()
                    .id(seriesId.toString())
                    .title(name)
                    .info(infoText)
                    .thumbnailUrl("/media/image/" + seriesId + "?v=2")
                    .videoUrl(videoUrl)
                    .country(countryVal != null ? countryVal.toLowerCase() : "th")
                    .build();
        }, params.toArray());
    }

    public int countSeriesWithFilters(String subType, Integer categoryId, Integer finalStatus, Integer year,
                                      String country) {
        StringBuilder sql = new StringBuilder("""
            SELECT COUNT(DISTINCT s.ID)
            FROM Series s
            WHERE (s.IsHidden = 0 OR s.IsHidden IS NULL)
              AND (s.SeriesType IN ('BL', 'GL') OR s.isBl = 1 OR EXISTS (SELECT 1 FROM SeriesCategories sc_all WHERE sc_all.SeriesID = s.ID AND sc_all.CategoryID IN (52, 63)))
        """);

        List<Object> params = new ArrayList<>();

        if ("gl".equalsIgnoreCase(subType)) {
            sql.append(" AND (s.SeriesType = 'GL' OR EXISTS (SELECT 1 FROM SeriesCategories sc_gl WHERE sc_gl.SeriesID = s.ID AND sc_gl.CategoryID = 63))");
        } else if ("bl".equalsIgnoreCase(subType)) {
            sql.append(" AND (s.SeriesType = 'BL' OR (s.isBl = 1 AND (s.SeriesType != 'GL' OR s.SeriesType IS NULL)) OR EXISTS (SELECT 1 FROM SeriesCategories sc_bl WHERE sc_bl.SeriesID = s.ID AND sc_bl.CategoryID = 52)) AND NOT EXISTS (SELECT 1 FROM SeriesCategories sc_gl2 WHERE sc_gl2.SeriesID = s.ID AND sc_gl2.CategoryID = 63) AND (s.SeriesType != 'GL' OR s.SeriesType IS NULL)");
        }

        if (categoryId != null && categoryId > 0) {
            sql.append(" AND EXISTS (SELECT 1 FROM SeriesCategories sc_cat WHERE sc_cat.SeriesID = s.ID AND sc_cat.CategoryID = ?)");
            params.add(categoryId);
        }

        if (finalStatus != null && finalStatus >= 0) {
            sql.append(" AND s.finalStatus = ?");
            params.add(finalStatus);
        }

        if (year != null && year > 0) {
            sql.append(" AND (EXISTS (SELECT 1 FROM Episode e WHERE e.SeriesId = s.ID AND e.ReleaseYear = ?) OR YEAR(s.uploadDate) = ?)");
            params.add(year);
            params.add(year);
        }

        if (country != null && !country.isEmpty() && !"all".equalsIgnoreCase(country)) {
            sql.append(" AND LOWER(s.Country) = LOWER(?)");
            params.add(country.trim());
        }

        Integer count = jdbcTemplate.queryForObject(sql.toString(), Integer.class, params.toArray());
        return count != null ? count : 0;
    }

    public List<FeaturedTvItemDto> findRecentBlEpisodes(int offset, int limit) {
        String sql = """
            WITH OrderedFeed AS (
                SELECT E.ID as EpisodeId, E.SeriesId as SeriesId, E.uploadDate, E.name, E.slug, E.SeasonNumber, E.EpisodeNumber,
                       S.name AS SeriesName, S.Language, S.Country, S.SeriesType, S.finalStatus,
                       S.uploadDate AS seriesUploadDate,
                       ROW_NUMBER() OVER (PARTITION BY E.SeriesId ORDER BY E.SeasonNumber DESC, E.EpisodeNumber DESC, E.uploadDate DESC, E.ID ASC) as latest_in_series_rn,
                       LAG(E.SeriesId) OVER (ORDER BY E.uploadDate DESC, E.SeasonNumber DESC, E.EpisodeNumber DESC, E.ID DESC) as prev_series,
                       LAG(E.SeasonNumber) OVER (ORDER BY E.uploadDate DESC, E.SeasonNumber DESC, E.EpisodeNumber DESC, E.ID DESC) as prev_season
                FROM Episode E
                JOIN Series S ON E.SeriesId = S.ID
                WHERE (S.SeriesType IN ('BL', 'GL') OR S.isBl = 1 OR EXISTS (SELECT 1 FROM SeriesCategories sc WHERE sc.SeriesID = S.ID AND sc.CategoryID IN (52, 63)))
                  AND (E.IsHidden = 0 OR E.IsHidden IS NULL)
                  AND (S.IsHidden = 0 OR S.IsHidden IS NULL)
            ),
            IslandFlags AS (
                SELECT *,
                       CASE 
                           WHEN prev_series IS NULL THEN 1
                           WHEN SeriesId != prev_series THEN 1
                           WHEN SeasonNumber != prev_season THEN 1
                           ELSE 0
                       END as is_new_island
                FROM OrderedFeed
            ),
            Islands AS (
                SELECT *,
                       SUM(is_new_island) OVER (ORDER BY uploadDate DESC, SeasonNumber DESC, EpisodeNumber DESC, EpisodeId DESC ROWS UNBOUNDED PRECEDING) as island_id
                FROM IslandFlags
            ),
            RankedInIsland AS (
                SELECT *,
                       ROW_NUMBER() OVER (PARTITION BY island_id ORDER BY SeasonNumber ASC, EpisodeNumber ASC, uploadDate ASC, EpisodeId ASC) as rn_first,
                       ROW_NUMBER() OVER (PARTITION BY island_id ORDER BY SeasonNumber DESC, EpisodeNumber DESC, uploadDate DESC, EpisodeId DESC) as rn_last
                FROM Islands
            ),
            DistinctSelected AS (
                SELECT EpisodeId, SeriesId, uploadDate, seriesUploadDate, name, slug, SeasonNumber, EpisodeNumber,
                       SeriesName, Language, Country, SeriesType, finalStatus,
                       CASE WHEN latest_in_series_rn = 1 THEN 1 ELSE 0 END AS isLatest
                FROM RankedInIsland
                WHERE rn_first = 1 OR rn_last = 1
            )
            SELECT * FROM DistinctSelected
            ORDER BY uploadDate DESC, SeasonNumber DESC, EpisodeNumber DESC, SeriesName ASC, EpisodeId ASC
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
            int isLatest = rs.getInt("isLatest");
            int finalStatus = (isLatest == 1) ? rs.getInt("finalStatus") : 0;
            String seriesType = rs.getString("SeriesType");
            java.sql.Timestamp uploadDate = rs.getTimestamp("uploadDate");
            java.sql.Timestamp seriesUploadDate = rs.getTimestamp("seriesUploadDate");

            boolean isNew = uploadDate != null && 
                    (System.currentTimeMillis() - uploadDate.getTime()) < (24L * 60 * 60 * 1000);
            boolean isSeriesNew = seriesUploadDate != null && 
                    (System.currentTimeMillis() - seriesUploadDate.getTime()) < (24L * 60 * 60 * 1000);

            return FeaturedTvItemDto.builder()
                    .id(epId)
                    .seriesId(sId)
                    .title(sName)
                    .season(season)
                    .episode(episode)
                    .slug(slug != null && !slug.isEmpty() ? slug : epId.toString())
                    .image("/media/image/" + sId + "?v=2")
                    .country(country != null && !country.isEmpty() ? country.toLowerCase() : mapLangToCode(lang))
                    .language(lang)
                    .finalStatus(finalStatus)
                    .seriesType(seriesType)
                    .isNew(isNew)
                    .isSeriesNew(isSeriesNew)
                    .uploadDate(uploadDate != null ? uploadDate.getTime() : null)
                    .seriesUploadDate(seriesUploadDate != null ? seriesUploadDate.getTime() : null)
                    .isFinal(finalStatus == 1)
                    .build();
        }, offset, limit);
    }

    public int countRecentBlEpisodes() {
        String sql = """
            WITH OrderedFeed AS (
                SELECT E.ID as EpisodeId, E.SeriesId as SeriesId, E.uploadDate, E.SeasonNumber, E.EpisodeNumber,
                       LAG(E.SeriesId) OVER (ORDER BY E.uploadDate DESC, E.SeasonNumber DESC, E.EpisodeNumber DESC, E.ID DESC) as prev_series,
                       LAG(E.SeasonNumber) OVER (ORDER BY E.uploadDate DESC, E.SeasonNumber DESC, E.EpisodeNumber DESC, E.ID DESC) as prev_season
                FROM Episode E
                JOIN Series S ON E.SeriesId = S.ID
                WHERE (S.SeriesType IN ('BL', 'GL') OR S.isBl = 1 OR EXISTS (SELECT 1 FROM SeriesCategories sc WHERE sc.SeriesID = S.ID AND sc.CategoryID IN (52, 63)))
                  AND (E.IsHidden = 0 OR E.IsHidden IS NULL)
                  AND (S.IsHidden = 0 OR S.IsHidden IS NULL)
            ),
            IslandFlags AS (
                SELECT *,
                       CASE 
                           WHEN prev_series IS NULL THEN 1
                           WHEN SeriesId != prev_series THEN 1
                           WHEN SeasonNumber != prev_season THEN 1
                           ELSE 0
                       END as is_new_island
                FROM OrderedFeed
            ),
            Islands AS (
                SELECT *,
                       SUM(is_new_island) OVER (ORDER BY uploadDate DESC, SeasonNumber DESC, EpisodeNumber DESC, EpisodeId DESC ROWS UNBOUNDED PRECEDING) as island_id
                FROM IslandFlags
            ),
            RankedInIsland AS (
                SELECT *,
                       ROW_NUMBER() OVER (PARTITION BY island_id ORDER BY SeasonNumber ASC, EpisodeNumber ASC, uploadDate ASC, EpisodeId ASC) as rn_first,
                       ROW_NUMBER() OVER (PARTITION BY island_id ORDER BY SeasonNumber DESC, EpisodeNumber DESC, uploadDate DESC, EpisodeId DESC) as rn_last
                FROM Islands
            )
            SELECT COUNT(*) FROM RankedInIsland
            WHERE rn_first = 1 OR rn_last = 1
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
            WHERE (s.SeriesType IN ('BL', 'GL') OR s.isBl = 1 OR EXISTS (SELECT 1 FROM SeriesCategories sc WHERE sc.SeriesID = s.ID AND sc.CategoryID IN (52, 63)))
              AND (s.IsHidden = 0 OR s.IsHidden IS NULL)
              AND s.name LIKE ?
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
            map.put("thumbnailUrl", "/media/image/" + rs.getString("ID") + "?v=2");
            return map;
        }, wild, wild);
    }

    public Map<String, Object> findSeriesDetails(UUID id) {
        String sql = """
            SELECT s.ID, s.name, s.Summary, s.Country, s.Language, s.SeriesType, s.finalStatus, s.slug, s.viewCount, s.IsHidden,
                   (SELECT STRING_AGG(c.Name, ', ') FROM Categories c 
                    JOIN SeriesCategories sc2 ON sc2.CategoryID = c.ID 
                    WHERE sc2.SeriesID = s.ID) as Category
            FROM Series s
            WHERE s.ID = ? AND (s.IsHidden = 0 OR s.IsHidden IS NULL)
        """;
        try {
            return jdbcTemplate.queryForMap(sql, id.toString());
        } catch (Exception e) {
            return null;
        }
    }

    public Map<String, Object> findEpisodeDetails(UUID id) {
        String sql = """
            SELECT e.ID, e.name, e.SeriesId, e.SeasonNumber, e.EpisodeNumber, e.DurationMinutes, e.slug, e.IsAdult, e.IsHidden
            FROM Episode e
            WHERE e.ID = ? AND (e.IsHidden = 0 OR e.IsHidden IS NULL)
        """;
        try {
            return jdbcTemplate.queryForMap(sql, id.toString());
        } catch (Exception e) {
            return null;
        }
    }

    public Map<String, Object> findEpisodeBySlug(String slug) {
        String sql = """
            SELECT TOP 1 e.ID, e.name, e.SeriesId, e.SeasonNumber, e.EpisodeNumber, e.DurationMinutes, e.slug, e.IsAdult, e.IsHidden
            FROM Episode e
            JOIN Series s ON e.SeriesId = s.ID
            WHERE e.slug = ? AND (e.IsHidden = 0 OR e.IsHidden IS NULL) AND (s.IsHidden = 0 OR s.IsHidden IS NULL)
        """;
        try {
            return jdbcTemplate.queryForMap(sql, slug);
        } catch (Exception e) {
            return null;
        }
    }

    public Map<String, Object> findSeriesBySlug(String slug) {
        String sql = """
            SELECT TOP 1 s.ID, s.name, s.Summary, s.Country, s.Language, s.SeriesType, s.finalStatus, s.slug, s.viewCount, s.IsHidden,
                   (SELECT STRING_AGG(c.Name, ', ') FROM Categories c 
                    JOIN SeriesCategories sc2 ON sc2.CategoryID = c.ID 
                    WHERE sc2.SeriesID = s.ID) as Category
            FROM Series s
            WHERE s.slug = ? AND (s.IsHidden = 0 OR s.IsHidden IS NULL)
        """;
        try {
            return jdbcTemplate.queryForMap(sql, slug);
        } catch (Exception e) {
            return null;
        }
    }

    public UUID findFirstEpisodeIdBySeriesId(UUID seriesId) {
        String sql = """
            SELECT TOP 1 ID FROM Episode
            WHERE SeriesId = ? AND (IsHidden = 0 OR IsHidden IS NULL)
            ORDER BY SeasonNumber ASC, EpisodeNumber ASC
        """;
        try {
            String epIdStr = jdbcTemplate.queryForObject(sql, String.class, seriesId.toString());
            return epIdStr != null ? UUID.fromString(epIdStr) : null;
        } catch (Exception e) {
            return null;
        }
    }

    public Map<String, Object> findSeriesByEpisodeId(UUID episodeId) {
        String sql = """
            SELECT s.ID, s.name, s.Summary, s.Country, s.Language, s.SeriesType, s.finalStatus, s.slug, s.viewCount,
                   (SELECT STRING_AGG(c.Name, ', ') FROM Categories c 
                    JOIN SeriesCategories sc2 ON sc2.CategoryID = c.ID 
                    WHERE sc2.SeriesID = s.ID) as Category
            FROM Series s
            JOIN Episode e ON e.SeriesId = s.ID
            WHERE e.ID = ? AND (s.IsHidden = 0 OR s.IsHidden IS NULL)
        """;
        try {
            return jdbcTemplate.queryForMap(sql, episodeId.toString());
        } catch (Exception e) {
            return null;
        }
    }

    public List<EpisodeViewModel> findEpisodesBySeriesId(UUID seriesId) {
        String sql = """
            SELECT ID, name, SeasonNumber, EpisodeNumber, DurationMinutes, slug
            FROM Episode
            WHERE SeriesId = ? AND (IsHidden = 0 OR IsHidden IS NULL)
            ORDER BY SeasonNumber ASC, EpisodeNumber ASC
        """;
        try {
            List<EpisodeViewModel> list = jdbcTemplate.query(sql, (rs, rowNum) -> {
                EpisodeViewModel vm = new EpisodeViewModel();
                vm.setId(UUID.fromString(rs.getString("ID")));
                vm.setName(rs.getString("name"));
                vm.setSeasonNumber(rs.getInt("SeasonNumber"));
                vm.setEpisodeNumber(rs.getInt("EpisodeNumber"));
                vm.setDuration(rs.getInt("DurationMinutes"));
                vm.setSlug(rs.getString("slug"));
                return vm;
            }, seriesId.toString());

            if (list.isEmpty()) {
                try {
                    String parentSeriesId = jdbcTemplate.queryForObject(
                            "SELECT SeriesId FROM Episode WHERE ID = ? AND (IsHidden = 0 OR IsHidden IS NULL)",
                            String.class, seriesId.toString());
                    if (parentSeriesId != null) {
                        return findEpisodesBySeriesId(UUID.fromString(parentSeriesId));
                    }
                } catch (Exception ignored) {}
            }
            return list;
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    public int countEpisodesBySeriesId(UUID seriesId) {
        String sql = "SELECT COUNT(*) FROM Episode WHERE SeriesId = ? AND (IsHidden = 0 OR IsHidden IS NULL)";
        try {
            Integer count = jdbcTemplate.queryForObject(sql, Integer.class, seriesId.toString());
            return count != null ? count : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    public boolean isValidSlug(String slug) {
        try {
            Integer countEp = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM Episode WHERE slug = ? AND (IsHidden = 0 OR IsHidden IS NULL)",
                    Integer.class, slug);
            if (countEp != null && countEp > 0) return true;

            Integer countSeries = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM Series WHERE slug = ? AND (IsHidden = 0 OR IsHidden IS NULL)",
                    Integer.class, slug);
            return countSeries != null && countSeries > 0;
        } catch (Exception e) {
            return false;
        }
    }

    public void incrementViewCount(UUID id) {
        try {
            if (id != null) {
                jdbcTemplate.update("EXEC IncrementViewCount @ID = ?", id.toString());
            }
        } catch (Exception ignored) {}
    }

    public List<Map<String, Object>> findSimilarContent(UUID contentId, int limit) {
        UUID targetId = contentId;
        try {
            String parentSeries = jdbcTemplate.queryForObject(
                    "SELECT SeriesId FROM Episode WHERE ID = ?", String.class, contentId.toString());
            if (parentSeries != null) {
                targetId = UUID.fromString(parentSeries);
            }
        } catch (Exception ignored) {}

        String sql = """
            WITH TargetCategories AS (
                SELECT CategoryID FROM SeriesCategories WHERE SeriesID = ?
                UNION ALL
                SELECT CategoryID FROM MovieCategories WHERE MovieID = ?
            ),
            SimilarSeries AS (
                SELECT S.ID, S.Name, 'soap_opera' as Type, S.viewCount,
                       (SELECT STRING_AGG(C.Name, ', ') FROM Categories C JOIN SeriesCategories SC2 ON SC2.CategoryID = C.ID WHERE SC2.SeriesID = S.ID) as Category,
                       COUNT(SC.CategoryID) as MatchCount,
                       (SELECT TOP 1 E.slug FROM Episode E WHERE E.SeriesId = S.ID AND (E.IsHidden = 0 OR E.IsHidden IS NULL) ORDER BY SeasonNumber ASC, EpisodeNumber ASC) as slug
                FROM Series S
                JOIN SeriesCategories SC ON S.ID = SC.SeriesID
                WHERE SC.CategoryID IN (SELECT CategoryID FROM TargetCategories)
                  AND S.ID <> ?
                  AND (S.SeriesType IN ('BL', 'GL') OR S.isBl = 1 OR EXISTS (SELECT 1 FROM SeriesCategories sc2 WHERE sc2.SeriesID = S.ID AND sc2.CategoryID IN (52, 63)))
                  AND (S.IsHidden = 0 OR S.IsHidden IS NULL)
                GROUP BY S.ID, S.Name, S.viewCount
            ),
            SimilarMovies AS (
                SELECT M.ID, M.Name, 'movie' as Type, M.viewCount,
                       (SELECT STRING_AGG(C.Name, ', ') FROM Categories C JOIN MovieCategories MC2 ON MC2.CategoryID = C.ID WHERE MC2.MovieID = M.ID) as Category,
                       COUNT(MC.CategoryID) as MatchCount,
                       M.slug as slug
                FROM Movie M
                JOIN MovieCategories MC ON M.ID = MC.MovieID
                WHERE MC.CategoryID IN (SELECT CategoryID FROM TargetCategories)
                  AND M.ID <> ?
                  AND (M.IsHidden = 0 OR M.IsHidden IS NULL)
                  AND EXISTS (SELECT 1 FROM MovieCategories MC_BL WHERE MC_BL.MovieID = M.ID AND MC_BL.CategoryID IN (52, 63))
                GROUP BY M.ID, M.Name, M.viewCount, M.slug
            )
            SELECT TOP (?) ID, Name, Type, Category, viewCount, slug FROM (
                SELECT * FROM SimilarSeries
                UNION ALL
                SELECT * FROM SimilarMovies
            ) Final
            ORDER BY MatchCount DESC, viewCount DESC
        """;

        try {
            List<Map<String, Object>> results = jdbcTemplate.query(sql, (rs, rowNum) -> {
                Map<String, Object> map = new HashMap<>();
                map.put("ID", rs.getString("ID"));
                map.put("Name", rs.getString("Name"));
                map.put("Category", rs.getString("Category"));
                map.put("Type", rs.getString("Type"));
                map.put("viewCount", rs.getInt("viewCount"));
                try {
                    map.put("slug", rs.getString("slug"));
                } catch (Exception ignored) {}
                return map;
            }, targetId.toString(), targetId.toString(), targetId.toString(), targetId.toString(), limit);

            if (results == null || results.isEmpty()) {
                String fallbackSql = """
                    SELECT TOP (?) ID, Name, Type, Category, viewCount, slug FROM (
                        SELECT s.ID, s.Name, 'soap_opera' as Type, s.viewCount,
                               (SELECT STRING_AGG(c.Name, ', ') FROM Categories c JOIN SeriesCategories sc2 ON sc2.CategoryID = c.ID WHERE sc2.SeriesID = s.ID) as Category,
                               (SELECT TOP 1 e.slug FROM Episode e WHERE e.SeriesId = s.ID AND (e.IsHidden = 0 OR e.IsHidden IS NULL) ORDER BY SeasonNumber ASC, EpisodeNumber ASC) as slug
                        FROM Series s
                        WHERE (s.SeriesType IN ('BL', 'GL') OR s.isBl = 1 OR EXISTS (SELECT 1 FROM SeriesCategories sc WHERE sc.SeriesID = s.ID AND sc.CategoryID IN (52, 63)))
                          AND s.ID <> ?
                          AND (s.IsHidden = 0 OR s.IsHidden IS NULL)
                        UNION ALL
                        SELECT m.ID, m.Name, 'movie' as Type, m.viewCount,
                               (SELECT STRING_AGG(c.Name, ', ') FROM Categories c JOIN MovieCategories mc2 ON mc2.CategoryID = c.ID WHERE mc2.MovieID = m.ID) as Category,
                               m.slug as slug
                        FROM Movie m
                        JOIN MovieCategories mc ON m.ID = mc.MovieID
                        WHERE mc.CategoryID IN (52, 63)
                          AND m.ID <> ?
                          AND (m.IsHidden = 0 OR m.IsHidden IS NULL)
                        GROUP BY m.ID, m.Name, m.viewCount, m.slug
                    ) Fallback
                    ORDER BY viewCount DESC
                """;
                results = jdbcTemplate.query(fallbackSql, (rs, rowNum) -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("ID", rs.getString("ID"));
                    map.put("Name", rs.getString("Name"));
                    map.put("Category", rs.getString("Category"));
                    map.put("Type", rs.getString("Type"));
                    map.put("viewCount", rs.getInt("viewCount"));
                    try {
                        map.put("slug", rs.getString("slug"));
                    } catch (Exception ignored) {}
                    return map;
                }, limit, targetId.toString(), targetId.toString());
            }

            return results;
        } catch (Exception e) {
            return Collections.emptyList();
        }
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
