package com.ses.bldizi.repository;

import com.ses.bldizi.model.CustomPage;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Optional;
import java.util.UUID;

@Repository
@SuppressWarnings({"SqlResolve", "SqlNoDataSourceInspection", "unused"})
public class CustomPageRepository {

    private final JdbcTemplate jdbcTemplate;

    public CustomPageRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @SuppressWarnings("unused")
    private final RowMapper<CustomPage> rowMapper = (rs, rowNum) -> {
        CustomPage page = new CustomPage();
        Object idObj = rs.getObject("id");
        if (idObj instanceof UUID u) {
            page.setId(u);
        } else if (idObj != null) {
            page.setId(UUID.fromString(idObj.toString()));
        }

        page.setTitle(rs.getString("title"));
        page.setSlug(rs.getString("slug"));
        page.setEntryFile(rs.getString("entryFile"));

        try {
            String site = rs.getString("targetSite");
            page.setTargetSite(site != null ? site : "whodatidols");
        } catch (SQLException ignored) {
            page.setTargetSite("whodatidols");
        }

        page.setTotalSize(rs.getLong("totalSize"));
        page.setFileCount(rs.getInt("fileCount"));
        page.setActive(rs.getBoolean("isActive"));

        Timestamp ct = rs.getTimestamp("createdAt");
        if (ct != null) page.setCreatedAt(ct.toInstant());
        Timestamp ut = rs.getTimestamp("updatedAt");
        if (ut != null) page.setUpdatedAt(ut.toInstant());

        return page;
    };

    public Optional<CustomPage> findActiveBySlug(String slug) {
        String sql = "SELECT * FROM CustomPages WHERE slug = ? AND isActive = 1";
        try {
            CustomPage page = jdbcTemplate.queryForObject(sql, rowMapper, slug);
            return Optional.ofNullable(page);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }
}