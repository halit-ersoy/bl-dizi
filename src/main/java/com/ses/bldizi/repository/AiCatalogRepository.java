package com.ses.bldizi.repository;

import com.ses.bldizi.model.AiCatalogItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;

@Repository
@SuppressWarnings({"SqlResolve", "SqlNoDataSourceInspection", "unused", "RedundantOrderingDirection"})
public class AiCatalogRepository {

    private static final Logger logger = LoggerFactory.getLogger(AiCatalogRepository.class);
    private final JdbcTemplate jdbcTemplate;

    public AiCatalogRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<AiCatalogItem> findAllActiveCatalogItems() {
        List<AiCatalogItem> items = new ArrayList<>();

        // 1. BL Movies
        //language=none
        String movieSql = """
            SELECT
                CAST(M.ID AS NVARCHAR(36)) AS id,
                M.name AS name,
                'Film' AS type,
                M.ReleaseYear AS year,
                M.slug AS slug,
                ISNULL(M.Summary, '') AS summary,
                ISNULL((SELECT STRING_AGG(C.Name, ', ') FROM Categories C JOIN MovieCategories MC ON MC.CategoryID = C.ID WHERE MC.MovieID = M.ID), '') AS category
            FROM Movie M
            WHERE (M.IsHidden = 0 OR M.IsHidden IS NULL)
              AND (M.isBl = 1 OR EXISTS (SELECT 1 FROM MovieCategories MC_BL WHERE MC_BL.MovieID = M.ID AND MC_BL.CategoryID IN (52, 63)))
        """;

        try {
            List<AiCatalogItem> movies = jdbcTemplate.query(movieSql, (rs, _) -> {
                AiCatalogItem item = new AiCatalogItem();
                item.setId(rs.getString("id"));
                item.setName(rs.getString("name"));
                item.setType(rs.getString("type"));
                item.setYear((Integer) rs.getObject("year"));
                item.setSlug(rs.getString("slug"));
                item.setSummary(rs.getString("summary"));
                item.setCategory(rs.getString("category"));
                return item;
            });
            items.addAll(movies);
        } catch (Exception e) {
            logger.warn("Could not query full movies for AI catalog with categories: {}. Retrying basic query...", e.getMessage());
            try {
                //language=none
                String basicMovieSql = """
                    SELECT CAST(M.ID AS NVARCHAR(36)) AS id, M.name, 'Film' AS type, M.ReleaseYear AS year, M.slug, ISNULL(M.Summary, '') AS summary
                    FROM Movie M
                    WHERE (M.IsHidden = 0 OR M.IsHidden IS NULL)
                      AND (M.isBl = 1 OR EXISTS (SELECT 1 FROM MovieCategories MC_BL WHERE MC_BL.MovieID = M.ID AND MC_BL.CategoryID IN (52, 63)))
                """;
                List<AiCatalogItem> movies = jdbcTemplate.query(basicMovieSql, (rs, _) -> {
                    AiCatalogItem item = new AiCatalogItem();
                    item.setId(rs.getString("id"));
                    item.setName(rs.getString("name"));
                    item.setType(rs.getString("type"));
                    item.setYear((Integer) rs.getObject("year"));
                    item.setSlug(rs.getString("slug"));
                    item.setSummary(rs.getString("summary"));
                    item.setCategory("");
                    return item;
                });
                items.addAll(movies);
            } catch (Exception ex) {
                logger.error("Failed to query basic movies for AI catalog: {}", ex.getMessage());
            }
        }

        // 2. BL Series
        //language=none
        String seriesSql = """
            SELECT
                CAST(S.ID AS NVARCHAR(36)) AS id,
                S.name AS name,
                'Dizi' AS type,
                ISNULL((SELECT TOP 1 E.slug FROM Episode E WHERE E.SeriesId = S.ID AND (E.IsHidden = 0 OR E.IsHidden IS NULL) ORDER BY SeasonNumber, EpisodeNumber), S.slug) AS slug,
                ISNULL(S.Summary, '') AS summary,
                ISNULL((SELECT STRING_AGG(C.Name, ', ') FROM Categories C JOIN SeriesCategories SC ON SC.CategoryID = C.ID WHERE SC.SeriesID = S.ID), '') AS category
            FROM Series S
            WHERE (S.IsHidden = 0 OR S.IsHidden IS NULL)
              AND (S.SeriesType IN ('BL', 'GL') OR S.isBl = 1 OR EXISTS (SELECT 1 FROM SeriesCategories SC_BL WHERE SC_BL.SeriesID = S.ID AND SC_BL.CategoryID IN (52, 63)))
        """;

        try {
            List<AiCatalogItem> series = jdbcTemplate.query(seriesSql, (rs, _) -> {
                AiCatalogItem item = new AiCatalogItem();
                item.setId(rs.getString("id"));
                item.setName(rs.getString("name"));
                item.setType(rs.getString("type"));
                item.setYear(null);
                item.setSlug(rs.getString("slug"));
                item.setSummary(rs.getString("summary"));
                item.setCategory(rs.getString("category"));
                return item;
            });
            items.addAll(series);
        } catch (Exception e) {
            logger.warn("Could not query full series for AI catalog with categories: {}. Retrying basic query...", e.getMessage());
            try {
                //language=none
                String basicSeriesSql = """
                    SELECT CAST(S.ID AS NVARCHAR(36)) AS id, S.name, 'Dizi' AS type, S.slug, ISNULL(S.Summary, '') AS summary
                    FROM Series S
                    WHERE (S.IsHidden = 0 OR S.IsHidden IS NULL)
                      AND (S.SeriesType = 'BL' OR S.isBl = 1)
                """;
                List<AiCatalogItem> series = jdbcTemplate.query(basicSeriesSql, (rs, _) -> {
                    AiCatalogItem item = new AiCatalogItem();
                    item.setId(rs.getString("id"));
                    item.setName(rs.getString("name"));
                    item.setType(rs.getString("type"));
                    item.setYear(null);
                    item.setSlug(rs.getString("slug"));
                    item.setSummary(rs.getString("summary"));
                    item.setCategory("");
                    return item;
                });
                items.addAll(series);
            } catch (Exception ex) {
                logger.error("Failed to query basic series for AI catalog: {}", ex.getMessage());
            }
        }

        return items;
    }
}
