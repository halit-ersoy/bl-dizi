package com.ses.bldizi.repository;

import com.ses.bldizi.model.FeaturedTvItemDto;
import com.ses.bldizi.model.VideoViewModel;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Repository
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
            JOIN MovieCategories mc ON m.ID = mc.MovieID
            WHERE mc.CategoryID IN (52, 63)
              AND (m.IsHidden = 0 OR m.IsHidden IS NULL)
            GROUP BY m.ID, m.name, m.Summary, m.DurationMinutes, m.Language, m.Country, m.ReleaseYear, m.slug, m.viewCount
            ORDER BY m.viewCount DESC
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
                    (System.currentTimeMillis() - uploadDate.getTime()) < (7L * 24 * 60 * 60 * 1000);

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
