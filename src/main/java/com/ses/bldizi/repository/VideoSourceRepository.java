package com.ses.bldizi.repository;

import com.ses.bldizi.model.VideoSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Repository
public class VideoSourceRepository {
    private static final Logger logger = LoggerFactory.getLogger(VideoSourceRepository.class);

    private final JdbcTemplate jdbcTemplate;

    public VideoSourceRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private final RowMapper<VideoSource> rowMapper = (rs, rowNum) -> {
        VideoSource source = new VideoSource();
        String idStr = rs.getString("ID");
        source.setId(idStr != null ? UUID.fromString(idStr) : null);

        String contentIdStr = rs.getString("ContentId");
        source.setContentId(contentIdStr != null ? UUID.fromString(contentIdStr) : null);

        source.setSourceName(rs.getString("SourceName") != null ? rs.getString("SourceName") : "Bilinmeyen Kaynak");
        source.setSourceUrl(rs.getString("SourceUrl") != null ? rs.getString("SourceUrl") : "");
        source.setSortOrder(rs.getInt("SortOrder"));
        return source;
    };

    public List<VideoSource> findByContentId(UUID contentId) {
        try {
            String sql = "EXEC dbo.GetVideoSources ?";
            return jdbcTemplate.query(sql, rowMapper, contentId.toString());
        } catch (Exception e) {
            try {
                String fallbackSql = "SELECT ID, ContentId, SourceName, SourceUrl, SortOrder FROM VideoSource WHERE ContentId = ? ORDER BY SortOrder ASC";
                return jdbcTemplate.query(fallbackSql, rowMapper, contentId.toString());
            } catch (Exception ex) {
                logger.error("Error retrieving video sources for id {}: {}", contentId, ex.getMessage());
                return Collections.emptyList();
            }
        }
    }
}
