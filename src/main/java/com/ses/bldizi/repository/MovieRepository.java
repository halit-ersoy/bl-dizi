package com.ses.bldizi.repository;

import com.ses.bldizi.model.FeaturedTvItemDto;
import com.ses.bldizi.model.VideoViewModel;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Repository
@SuppressWarnings({"SqlResolve", "SqlNoDataSourceInspection", "DuplicatedCode", "unused", "ConstantConditions"})
public class MovieRepository {

    private final JdbcTemplate jdbcTemplate;

    public MovieRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<VideoViewModel> findTop6BlMoviesByViews() {
        String sql = """
            SELECT TOP 6 m.ID, m.name, m.Summary, m.DurationMinutes, m.Language, m.Country, m.ReleaseYear, m.slug, m.viewCount,
                   (SELECT STRING_AGG(c.Name, ', ') FROM Categories c 
                    JOIN MovieCategories mc2 ON mc2.CategoryID = c.ID 
                    WHERE mc2.MovieID = m.ID) as Category
            FROM Movie m
            LEFT JOIN (
                SELECT ContentId, COUNT(*) as WeeklyViews
                FROM [dbo].[ContentViewLog]
                WHERE [ViewedAt] >= DATEADD(day, -7, GETDATE())
                GROUP BY ContentId
            ) V ON m.ID = V.ContentId
            WHERE (m.IsHidden = 0 OR m.IsHidden IS NULL)
              AND (m.MovieType IN ('BL', 'GL') OR m.isBl = 1 OR EXISTS (SELECT 1 FROM MovieCategories mc WHERE mc.MovieID = m.ID AND mc.CategoryID IN (52, 63)))
            ORDER BY ISNULL(V.WeeklyViews, 0) DESC, m.viewCount DESC, m.name ASC
        """;

        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            String idStr = rs.getString("ID");
            UUID movieId = UUID.fromString(idStr);
            String name = rs.getString("name");
            String category = rs.getString("Category");
            String country = rs.getString("Country");
            String slug = rs.getString("slug");
            int duration = rs.getInt("DurationMinutes");
            int year = rs.getInt("ReleaseYear");

            String mainCategory = category != null && category.contains(",") ? category.split(",")[0].trim() : (category != null ? category : "BL Film");
            String durationText = duration > 0 ? " • " + duration + " dk" : "";
            String yearText = year > 0 ? year + " • " : "";

            String videoUrl = (slug != null && !slug.isEmpty()) ? "/" + slug : "/" + movieId;

            return VideoViewModel.builder()
                    .id(movieId.toString())
                    .title(name)
                    .info(yearText + mainCategory + durationText)
                    .thumbnailUrl("/media/image/" + movieId + "?v=2")
                    .videoUrl(videoUrl)
                    .country(country != null ? country.toLowerCase() : "th")
                    .build();
        });
    }

    public List<VideoViewModel> findRecentMoviesPaged(int offset, int limit) {
        String sql = """
            SELECT m.ID, m.name, m.Summary, m.DurationMinutes, m.Language, m.Country, m.ReleaseYear, m.slug, m.uploadDate,
                   (SELECT STRING_AGG(c.Name, ', ') FROM Categories c 
                    JOIN MovieCategories mc2 ON mc2.CategoryID = c.ID 
                    WHERE mc2.MovieID = m.ID) as Category
            FROM Movie m
            JOIN MovieCategories mc ON m.ID = mc.MovieID
            WHERE mc.CategoryID IN (52, 63)
              AND (m.IsHidden = 0 OR m.IsHidden IS NULL)
            GROUP BY m.ID, m.name, m.Summary, m.DurationMinutes, m.Language, m.Country, m.ReleaseYear, m.slug, m.uploadDate
            ORDER BY m.uploadDate DESC
            OFFSET ? ROWS FETCH NEXT ? ROWS ONLY
        """;

        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            String idStr = rs.getString("ID");
            UUID movieId = UUID.fromString(idStr);
            String name = rs.getString("name");
            String category = rs.getString("Category");
            String country = rs.getString("Country");
            String slug = rs.getString("slug");
            int duration = rs.getInt("DurationMinutes");
            int year = rs.getInt("ReleaseYear");

            String mainCategory = category != null && category.contains(",") ? category.split(",")[0].trim() : (category != null ? category : "BL Film");
            String durationText = duration > 0 ? " • " + duration + " dk" : "";
            String yearText = year > 0 ? year + " • " : "";
            String videoUrl = (slug != null && !slug.isEmpty()) ? "/" + slug : "/" + movieId;

            return VideoViewModel.builder()
                    .id(movieId.toString())
                    .title(name)
                    .info(yearText + mainCategory + durationText)
                    .thumbnailUrl("/media/image/" + movieId + "?v=2")
                    .videoUrl(videoUrl)
                    .country(country != null ? country.toLowerCase() : "th")
                    .build();
        }, offset, limit);
    }

    public int countAllMovies() {
        String sql = """
            SELECT COUNT(DISTINCT m.ID)
            FROM Movie m
            JOIN MovieCategories mc ON m.ID = mc.MovieID
            WHERE mc.CategoryID IN (52, 63)
              AND (m.IsHidden = 0 OR m.IsHidden IS NULL)
        """;
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class);
        return count != null ? count : 0;
    }

    public List<VideoViewModel> searchMoviesPaged(String query, int offset, int limit) {
        String sql = """
            SELECT m.ID, m.name, m.Summary, m.DurationMinutes, m.Language, m.Country, m.ReleaseYear, m.slug, m.uploadDate,
                   (SELECT STRING_AGG(c.Name, ', ') FROM Categories c 
                    JOIN MovieCategories mc2 ON mc2.CategoryID = c.ID 
                    WHERE mc2.MovieID = m.ID) as Category
            FROM Movie m
            JOIN MovieCategories mc ON m.ID = mc.MovieID
            WHERE mc.CategoryID IN (52, 63)
              AND (m.IsHidden = 0 OR m.IsHidden IS NULL)
              AND m.name LIKE ?
            GROUP BY m.ID, m.name, m.Summary, m.DurationMinutes, m.Language, m.Country, m.ReleaseYear, m.slug, m.uploadDate
            ORDER BY m.uploadDate DESC
            OFFSET ? ROWS FETCH NEXT ? ROWS ONLY
        """;
        String wild = "%" + query.trim() + "%";
        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            String idStr = rs.getString("ID");
            UUID movieId = UUID.fromString(idStr);
            String name = rs.getString("name");
            String category = rs.getString("Category");
            String country = rs.getString("Country");
            String slug = rs.getString("slug");
            int duration = rs.getInt("DurationMinutes");
            int year = rs.getInt("ReleaseYear");

            String mainCategory = category != null && category.contains(",") ? category.split(",")[0].trim() : (category != null ? category : "BL Film");
            String durationText = duration > 0 ? " • " + duration + " dk" : "";
            String yearText = year > 0 ? year + " • " : "";
            String videoUrl = (slug != null && !slug.isEmpty()) ? "/" + slug : "/" + movieId;

            return VideoViewModel.builder()
                    .id(movieId.toString())
                    .title(name)
                    .info(yearText + mainCategory + durationText)
                    .thumbnailUrl("/media/image/" + movieId + "?v=2")
                    .videoUrl(videoUrl)
                    .country(country != null ? country.toLowerCase() : "th")
                    .build();
        }, wild, offset, limit);
    }

    public int countMoviesBySearch(String query) {
        String sql = """
            SELECT COUNT(DISTINCT m.ID)
            FROM Movie m
            JOIN MovieCategories mc ON m.ID = mc.MovieID
            WHERE mc.CategoryID IN (52, 63)
              AND (m.IsHidden = 0 OR m.IsHidden IS NULL)
              AND m.name LIKE ?
        """;
        String wild = "%" + query.trim() + "%";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, wild);
        return count != null ? count : 0;
    }

    public List<VideoViewModel> findMoviesWithFilters(String subType, Integer categoryId, Integer year,
                                                      String country, String sort, int offset, int limit) {
        StringBuilder sql = new StringBuilder("""
            SELECT m.ID, m.name, m.Summary, m.DurationMinutes, m.Language, m.Country, m.ReleaseYear, m.slug, m.uploadDate,
                   (SELECT STRING_AGG(c.Name, ', ') FROM Categories c 
                    JOIN MovieCategories mc2 ON mc2.CategoryID = c.ID 
                    WHERE mc2.MovieID = m.ID) as Category
            FROM Movie m
            WHERE (m.IsHidden = 0 OR m.IsHidden IS NULL)
              AND (m.isBl = 1 OR EXISTS (SELECT 1 FROM MovieCategories mc_all WHERE mc_all.MovieID = m.ID AND mc_all.CategoryID IN (52, 63)))
        """);

        List<Object> params = new ArrayList<>();

        if ("gl".equalsIgnoreCase(subType)) {
            sql.append(" AND EXISTS (SELECT 1 FROM MovieCategories mc_gl WHERE mc_gl.MovieID = m.ID AND mc_gl.CategoryID = 63)");
        } else if ("bl".equalsIgnoreCase(subType)) {
            sql.append(" AND (EXISTS (SELECT 1 FROM MovieCategories mc_bl WHERE mc_bl.MovieID = m.ID AND mc_bl.CategoryID = 52) OR (m.isBl = 1 AND NOT EXISTS (SELECT 1 FROM MovieCategories mc_gl2 WHERE mc_gl2.MovieID = m.ID AND mc_gl2.CategoryID = 63)))");
        }

        if (categoryId != null && categoryId > 0) {
            sql.append(" AND EXISTS (SELECT 1 FROM MovieCategories mc_cat WHERE mc_cat.MovieID = m.ID AND mc_cat.CategoryID = ?)");
            params.add(categoryId);
        }

        if (year != null && year > 0) {
            sql.append(" AND m.ReleaseYear = ?");
            params.add(year);
        }

        if (country != null && !country.isEmpty() && !"all".equalsIgnoreCase(country)) {
            sql.append(" AND LOWER(m.Country) = LOWER(?)");
            params.add(country.trim());
        }

        if ("oldest".equalsIgnoreCase(sort)) {
            sql.append(" ORDER BY m.uploadDate ASC, m.name ASC");
        } else if ("a-z".equalsIgnoreCase(sort)) {
            sql.append(" ORDER BY m.name ASC");
        } else if ("z-a".equalsIgnoreCase(sort)) {
            sql.append(" ORDER BY m.name DESC");
        } else {
            sql.append(" ORDER BY m.uploadDate DESC, m.name ASC");
        }

        sql.append(" OFFSET ? ROWS FETCH NEXT ? ROWS ONLY");
        params.add(offset);
        params.add(limit);

        return jdbcTemplate.query(sql.toString(), (rs, rowNum) -> {
            String idStr = rs.getString("ID");
            UUID movieId = UUID.fromString(idStr);
            String name = rs.getString("name");
            String category = rs.getString("Category");
            String countryVal = rs.getString("Country");
            String slug = rs.getString("slug");
            int duration = rs.getInt("DurationMinutes");
            int releaseYr = rs.getInt("ReleaseYear");

            String mainCategory = category != null && category.contains(",") ? category.split(",")[0].trim() : (category != null ? category : "BL Film");
            String durationText = duration > 0 ? " • " + duration + " dk" : "";
            String yearText = releaseYr > 0 ? releaseYr + " • " : "";
            String videoUrl = (slug != null && !slug.isEmpty()) ? "/" + slug : "/" + movieId;

            return VideoViewModel.builder()
                    .id(movieId.toString())
                    .title(name)
                    .info(yearText + mainCategory + durationText)
                    .thumbnailUrl("/media/image/" + movieId + "?v=2")
                    .videoUrl(videoUrl)
                    .country(countryVal != null ? countryVal.toLowerCase() : "th")
                    .build();
        }, params.toArray());
    }

    public int countMoviesWithFilters(String subType, Integer categoryId, Integer year, String country) {
        StringBuilder sql = new StringBuilder("""
            SELECT COUNT(DISTINCT m.ID)
            FROM Movie m
            WHERE (m.IsHidden = 0 OR m.IsHidden IS NULL)
              AND (m.isBl = 1 OR EXISTS (SELECT 1 FROM MovieCategories mc_all WHERE mc_all.MovieID = m.ID AND mc_all.CategoryID IN (52, 63)))
        """);

        List<Object> params = new ArrayList<>();

        if ("gl".equalsIgnoreCase(subType)) {
            sql.append(" AND EXISTS (SELECT 1 FROM MovieCategories mc_gl WHERE mc_gl.MovieID = m.ID AND mc_gl.CategoryID = 63)");
        } else if ("bl".equalsIgnoreCase(subType)) {
            sql.append(" AND (EXISTS (SELECT 1 FROM MovieCategories mc_bl WHERE mc_bl.MovieID = m.ID AND mc_bl.CategoryID = 52) OR (m.isBl = 1 AND NOT EXISTS (SELECT 1 FROM MovieCategories mc_gl2 WHERE mc_gl2.MovieID = m.ID AND mc_gl2.CategoryID = 63)))");
        }

        if (categoryId != null && categoryId > 0) {
            sql.append(" AND EXISTS (SELECT 1 FROM MovieCategories mc_cat WHERE mc_cat.MovieID = m.ID AND mc_cat.CategoryID = ?)");
            params.add(categoryId);
        }

        if (year != null && year > 0) {
            sql.append(" AND m.ReleaseYear = ?");
            params.add(year);
        }

        if (country != null && !country.isEmpty() && !"all".equalsIgnoreCase(country)) {
            sql.append(" AND LOWER(m.Country) = LOWER(?)");
            params.add(country.trim());
        }

        Integer count = jdbcTemplate.queryForObject(sql.toString(), Integer.class, params.toArray());
        return count != null ? count : 0;
    }

    public List<FeaturedTvItemDto> findRecentMoviesForFeatured(int offset, int limit) {
        String sql = """
            SELECT m.ID, m.name, m.slug, m.Country, m.Language, m.uploadDate
            FROM Movie m
            JOIN MovieCategories mc ON m.ID = mc.MovieID
            WHERE mc.CategoryID IN (52, 63)
              AND (m.IsHidden = 0 OR m.IsHidden IS NULL)
            GROUP BY m.ID, m.name, m.slug, m.Country, m.Language, m.uploadDate
            ORDER BY m.uploadDate DESC
            OFFSET ? ROWS FETCH NEXT ? ROWS ONLY
        """;

        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            UUID movieId = UUID.fromString(rs.getString("ID"));
            String name = rs.getString("name");
            String slug = rs.getString("slug");
            String country = rs.getString("Country");
            String lang = rs.getString("Language");
            java.sql.Timestamp uploadDate = rs.getTimestamp("uploadDate");

            boolean isNew = uploadDate != null && 
                    (System.currentTimeMillis() - uploadDate.getTime()) < (24L * 60 * 60 * 1000);

            return FeaturedTvItemDto.builder()
                    .id(movieId)
                    .title(name)
                    .season(1)
                    .episode(1)
                    .slug(slug != null && !slug.isEmpty() ? slug : movieId.toString())
                    .image("/media/image/" + movieId + "?v=2")
                    .country(country != null && !country.isEmpty() ? country.toLowerCase() : "th")
                    .language(lang)
                    .isNew(isNew)
                    .isSeriesNew(false)
                    .uploadDate(uploadDate != null ? uploadDate.getTime() : null)
                    .seriesUploadDate(null)
                    .finalStatus(0)
                    .seriesType("Film")
                    .build();
        }, offset, limit);
    }

    public int countRecentMoviesForFeatured() {
        return countAllMovies();
    }

    public Map<String, Object> findMovieDetails(UUID id) {
        String sql = """
            SELECT m.ID, m.name, m.Summary, m.DurationMinutes, m.Language, m.Country, m.ReleaseYear, m.slug, m.viewCount, m.IsAdult, m.IsHidden,
                   (SELECT STRING_AGG(c.Name, ', ') FROM Categories c 
                    JOIN MovieCategories mc2 ON mc2.CategoryID = c.ID 
                    WHERE mc2.MovieID = m.ID) as Category
            FROM Movie m
            WHERE m.ID = ? AND (m.IsHidden = 0 OR m.IsHidden IS NULL)
        """;
        try {
            return jdbcTemplate.queryForMap(sql, id.toString());
        } catch (Exception e) {
            return null;
        }
    }

    public Map<String, Object> findMovieBySlug(String slug) {
        String sql = """
            SELECT TOP 1 m.ID, m.name, m.Summary, m.DurationMinutes, m.Language, m.Country, m.ReleaseYear, m.slug, m.viewCount, m.IsAdult, m.IsHidden,
                   (SELECT STRING_AGG(c.Name, ', ') FROM Categories c 
                    JOIN MovieCategories mc2 ON mc2.CategoryID = c.ID 
                    WHERE mc2.MovieID = m.ID) as Category
            FROM Movie m
            WHERE m.slug = ? AND (m.IsHidden = 0 OR m.IsHidden IS NULL)
        """;
        try {
            return jdbcTemplate.queryForMap(sql, slug);
        } catch (Exception e) {
            return null;
        }
    }

    public boolean isValidSlug(String slug) {
        try {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM Movie WHERE slug = ? AND (IsHidden = 0 OR IsHidden IS NULL)",
                    Integer.class, slug);
            return count != null && count > 0;
        } catch (Exception e) {
            return false;
        }
    }

    public void incrementViewCount(UUID id) {
        try {
            jdbcTemplate.update("UPDATE Movie SET viewCount = COALESCE(viewCount, 0) + 1 WHERE ID = ?", id.toString());
        } catch (Exception ignored) {}
    }
}
