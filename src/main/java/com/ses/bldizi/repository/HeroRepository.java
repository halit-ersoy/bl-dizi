package com.ses.bldizi.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.*;

@Repository
@SuppressWarnings({"SqlResolve", "SqlNoDataSourceInspection"})
public class HeroRepository {

    private final JdbcTemplate jdbcTemplate;

    public HeroRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<Map<String, Object>> getHeroVideos() {
        return getHeroVideos("bldizi");
    }

    public List<Map<String, Object>> getHeroVideos(String platform) {
        if (platform == null || platform.isBlank()) {
            platform = "bldizi";
        }
        platform = platform.trim().toLowerCase();

        String sql = """
                SELECT * FROM (
                  SELECT H.[ID], H.[ReferenceId], M.[name], M.[slug] as [slug],
                         (SELECT STRING_AGG(C.Name, ', ') FROM Categories C JOIN MovieCategories MC ON MC.CategoryID = C.ID WHERE MC.MovieID = M.ID) as [category],
                         H.[CustomSummary] as _content, 'Movie' AS [type], H.sortOrder, H.isImage, H.platform
                  FROM Hero H INNER JOIN Movie M ON H.ReferenceId = M.ID
                  WHERE H.platform = ? AND (M.IsHidden = 0 OR M.IsHidden IS NULL)
                  UNION ALL
                  SELECT H.[ID], H.[ReferenceId], S.[name],
                         (SELECT TOP 1 E.[slug] FROM Episode E WHERE E.SeriesId = S.ID AND (E.IsHidden = 0 OR E.IsHidden IS NULL) ORDER BY SeasonNumber ASC, EpisodeNumber ASC) as [slug],
                         (SELECT STRING_AGG(C.Name, ', ') FROM Categories C JOIN SeriesCategories SC ON SC.CategoryID = C.ID WHERE SC.SeriesID = S.ID) as [category],
                         H.[CustomSummary] as _content, 'SoapOpera' AS [type], H.sortOrder, H.isImage, H.platform
                  FROM Hero H INNER JOIN Series S ON H.ReferenceId = S.ID
                  WHERE H.platform = ? AND (S.IsHidden = 0 OR S.IsHidden IS NULL)
                ) AS Result ORDER BY sortOrder ASC
                """;

        List<Map<String, Object>> heroList = jdbcTemplate.queryForList(sql, platform, platform);

        // Fallback: If no heroes found for this platform, provide top BL series
        if (heroList.isEmpty()) {
            String fallbackSql = """
                SELECT TOP 5 s.ID as ID, s.ID as ReferenceId, s.name,
                       (SELECT TOP 1 e.slug FROM Episode e WHERE e.SeriesId = s.ID AND (e.IsHidden = 0 OR e.IsHidden IS NULL) ORDER BY SeasonNumber ASC, EpisodeNumber ASC) as slug,
                       (SELECT STRING_AGG(c.Name, ', ') FROM Categories c JOIN SeriesCategories sc ON sc.CategoryID = c.ID WHERE sc.SeriesID = s.ID) as category,
                       s.Summary as _content, 'SoapOpera' as type,
                       ROW_NUMBER() OVER (ORDER BY s.viewCount DESC) - 1 as sortOrder,
                       CAST(1 AS BIT) as isImage, ? as platform
                FROM Series s
                WHERE s.SeriesType = 'BL'
                  AND (s.IsHidden = 0 OR s.IsHidden IS NULL)
                ORDER BY s.viewCount DESC
            """;
            try {
                return jdbcTemplate.queryForList(fallbackSql, platform);
            } catch (Exception ignored) {}
        }

        return heroList;
    }
}
