package com.ses.bldizi.repository;

import com.ses.bldizi.model.HeroVideoDto;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.*;

@Repository
public class HeroRepository {

    private final JdbcTemplate jdbcTemplate;

    public HeroRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<HeroVideoDto> getHeroVideos() {
        // 1. First attempt to get from Hero table
        String heroSql = """
            SELECT h.ID as HeroId, h.ReferenceId, h.CustomSummary, h.sortOrder, h.isImage,
                   s.name as SeriesName, s.Summary as SeriesSummary, s.slug as SeriesSlug,
                   s.Country as Country, s.Language as Language,
                   (SELECT STRING_AGG(c.Name, ', ') FROM Categories c 
                    JOIN SeriesCategories sc ON sc.CategoryID = c.ID 
                    WHERE sc.SeriesID = s.ID) as Category
            FROM Hero h
            JOIN Series s ON h.ReferenceId = s.ID
            WHERE (s.IsHidden = 0 OR s.IsHidden IS NULL)
            ORDER BY h.sortOrder ASC
        """;

        List<HeroVideoDto> heroList = jdbcTemplate.query(heroSql, (rs, rowNum) -> {
            UUID heroId = UUID.fromString(rs.getString("HeroId"));
            UUID refId = UUID.fromString(rs.getString("ReferenceId"));
            String title = rs.getString("SeriesName");
            String customSumm = rs.getString("CustomSummary");
            String seriesSumm = rs.getString("SeriesSummary");
            String slug = rs.getString("SeriesSlug");
            String category = rs.getString("Category");
            String country = rs.getString("Country");
            String lang = rs.getString("Language");
            int sortOrder = rs.getInt("sortOrder");
            boolean isImage = rs.getBoolean("isImage");

            String summary = (customSumm != null && !customSumm.isBlank()) ? customSumm : seriesSumm;
            String videoUrl = (slug != null && !slug.isEmpty()) ? "/" + slug : "/" + refId;

            return HeroVideoDto.builder()
                    .id(heroId)
                    .referenceId(refId)
                    .title(title)
                    .summary(summary)
                    .category(category != null ? category : "BL Dizi")
                    .videoUrl(videoUrl)
                    .thumbnailUrl("/media/image/" + refId)
                    .country(country != null ? country.toLowerCase() : "th")
                    .language(lang)
                    .sortOrder(sortOrder)
                    .isImage(isImage)
                    .build();
        });

        // 2. If fewer than 4 heroes, supplement with top viewed BL series
        if (heroList.size() < 4) {
            Set<UUID> existingRefIds = new HashSet<>();
            for (HeroVideoDto h : heroList) {
                if (h.getReferenceId() != null) existingRefIds.add(h.getReferenceId());
            }

            String topSeriesSql = """
                SELECT TOP 5 s.ID, s.name, s.Summary, s.slug, s.Country, s.Language,
                       (SELECT STRING_AGG(c.Name, ', ') FROM Categories c 
                        JOIN SeriesCategories sc2 ON sc2.CategoryID = c.ID 
                        WHERE sc2.SeriesID = s.ID) as Category
                FROM Series s
                JOIN SeriesCategories sc ON s.ID = sc.SeriesID
                WHERE sc.CategoryID IN (52, 63)
                  AND (s.IsHidden = 0 OR s.IsHidden IS NULL)
                GROUP BY s.ID, s.name, s.Summary, s.slug, s.Country, s.Language, s.viewCount
                ORDER BY s.viewCount DESC
            """;

            List<HeroVideoDto> topSeries = jdbcTemplate.query(topSeriesSql, (rs, rowNum) -> {
                UUID sId = UUID.fromString(rs.getString("ID"));
                String title = rs.getString("name");
                String summary = rs.getString("Summary");
                String slug = rs.getString("slug");
                String category = rs.getString("Category");
                String country = rs.getString("Country");
                String lang = rs.getString("Language");

                String videoUrl = (slug != null && !slug.isEmpty()) ? "/" + slug : "/" + sId;

                return HeroVideoDto.builder()
                        .id(sId)
                        .referenceId(sId)
                        .title(title)
                        .summary(summary)
                        .category(category != null ? category : "BL Dizi")
                        .videoUrl(videoUrl)
                        .thumbnailUrl("/media/image/" + sId)
                        .country(country != null ? country.toLowerCase() : "th")
                        .language(lang)
                        .sortOrder(rowNum + 10)
                        .isImage(true)
                        .build();
            });

            for (HeroVideoDto s : topSeries) {
                if (!existingRefIds.contains(s.getReferenceId())) {
                    heroList.add(s);
                    existingRefIds.add(s.getReferenceId());
                    if (heroList.size() >= 5) break;
                }
            }
        }

        return heroList;
    }
}
