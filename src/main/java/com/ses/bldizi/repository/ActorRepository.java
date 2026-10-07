package com.ses.bldizi.repository;

import com.ses.bldizi.model.Actor;
import com.ses.bldizi.model.CastMemberDto;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.*;

@Repository
public class ActorRepository {

    private final JdbcTemplate jdbcTemplate;

    public ActorRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private final RowMapper<Actor> actorRowMapper = (rs, rowNum) -> {
        Actor actor = new Actor();
        String idStr = rs.getString("ID");
        actor.setId(idStr != null ? UUID.fromString(idStr) : null);
        actor.setName(rs.getString("Name"));
        actor.setPhotoUrl(rs.getString("PhotoUrl"));
        actor.setRemotePhotoUrl(rs.getString("RemotePhotoUrl"));

        int tmdbId = rs.getInt("TmdbId");
        if (!rs.wasNull()) actor.setTmdbId(tmdbId);

        int tvmazeId = rs.getInt("TvMazeId");
        if (!rs.wasNull()) actor.setTvmazeId(tvmazeId);

        actor.setBiography(rs.getString("Biography"));
        actor.setBirthday(rs.getString("Birthday"));
        actor.setDeathday(rs.getString("Deathday"));
        actor.setPlaceOfBirth(rs.getString("PlaceOfBirth"));
        actor.setKnownFor(rs.getString("KnownFor"));

        int gender = rs.getInt("Gender");
        if (!rs.wasNull()) actor.setGender(gender);

        java.sql.Timestamp ts = rs.getTimestamp("CreatedAt");
        if (ts != null) actor.setCreatedAt(ts.toInstant());

        return actor;
    };

    private final RowMapper<CastMemberDto> castMemberRowMapper = (rs, rowNum) -> {
        CastMemberDto dto = new CastMemberDto();
        String idStr = rs.getString("ActorId");
        dto.setActorId(idStr != null ? UUID.fromString(idStr) : null);
        dto.setName(rs.getString("Name"));
        dto.setCharacterName(rs.getString("CharacterName"));

        String photo = rs.getString("PhotoUrl");
        if (photo == null || photo.isEmpty()) {
            photo = "/media/actor/" + idStr;
        }
        dto.setPhotoUrl(photo);

        int tmdbId = rs.getInt("TmdbId");
        if (!rs.wasNull()) dto.setTmdbId(tmdbId);

        int tvmazeId = rs.getInt("TvMazeId");
        if (!rs.wasNull()) dto.setTvmazeId(tvmazeId);

        dto.setOrderIndex(rs.getInt("OrderIndex"));
        return dto;
    };

    public Actor findById(UUID actorId) {
        if (actorId == null) return null;
        try {
            List<Actor> list = jdbcTemplate.query(
                    "SELECT ID, Name, PhotoUrl, RemotePhotoUrl, TmdbId, TvMazeId, Biography, Birthday, Deathday, PlaceOfBirth, KnownFor, Gender, CreatedAt FROM [dbo].[Actor] WHERE ID = ?",
                    actorRowMapper, actorId.toString());
            return list.isEmpty() ? null : list.get(0);
        } catch (Exception e) {
            return null;
        }
    }

    public List<CastMemberDto> getCastForMovie(UUID movieId) {
        try {
            String sql = "SELECT a.ID as ActorId, a.Name, a.PhotoUrl, a.TmdbId, a.TvMazeId, ma.CharacterName, ma.OrderIndex " +
                    "FROM [dbo].[MovieActors] ma " +
                    "JOIN [dbo].[Actor] a ON ma.ActorID = a.ID " +
                    "WHERE ma.MovieID = ? " +
                    "ORDER BY ma.OrderIndex ASC";
            return jdbcTemplate.query(sql, castMemberRowMapper, movieId.toString());
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    public List<CastMemberDto> getCastForSeries(UUID seriesId) {
        try {
            String sql = "SELECT a.ID as ActorId, a.Name, a.PhotoUrl, a.TmdbId, a.TvMazeId, sa.CharacterName, sa.OrderIndex " +
                    "FROM [dbo].[SeriesActors] sa " +
                    "JOIN [dbo].[Actor] a ON sa.ActorID = a.ID " +
                    "WHERE sa.SeriesID = ? " +
                    "ORDER BY sa.OrderIndex ASC";
            return jdbcTemplate.query(sql, castMemberRowMapper, seriesId.toString());
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    public List<Map<String, Object>> getMoviesForActor(UUID actorId) {
        if (actorId == null) return Collections.emptyList();
        String sql = """
            SELECT m.ID, m.name, m.slug, m.ReleaseYear, m.DurationMinutes, m.Country,
                   ma.CharacterName, ma.OrderIndex,
                   (SELECT STRING_AGG(c.Name, ', ') FROM Categories c
                    JOIN MovieCategories mc ON mc.CategoryID = c.ID
                    WHERE mc.MovieID = m.ID) AS category
            FROM [dbo].[MovieActors] ma
            JOIN [dbo].[Movie] m ON ma.MovieID = m.ID
            WHERE ma.ActorID = ? 
              AND (m.IsHidden = 0 OR m.IsHidden IS NULL)
              AND EXISTS (SELECT 1 FROM MovieCategories mc_bl WHERE mc_bl.MovieID = m.ID AND mc_bl.CategoryID IN (52, 63))
            ORDER BY m.ReleaseYear DESC, m.name ASC
        """;
        try {
            return jdbcTemplate.query(sql, (rs, rowNum) -> {
                Map<String, Object> map = new HashMap<>();
                map.put("id", rs.getString("ID"));
                map.put("name", rs.getString("name"));
                map.put("slug", rs.getString("slug"));
                int year = rs.getInt("ReleaseYear");
                if (!rs.wasNull()) map.put("releaseYear", year);
                int duration = rs.getInt("DurationMinutes");
                if (!rs.wasNull()) map.put("durationMinutes", duration);
                map.put("country", rs.getString("Country"));
                map.put("characterName", rs.getString("CharacterName"));
                map.put("orderIndex", rs.getInt("OrderIndex"));
                map.put("category", rs.getString("category"));
                map.put("type", "movie");
                return map;
            }, actorId.toString());
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    public List<Map<String, Object>> getSeriesForActor(UUID actorId) {
        if (actorId == null) return Collections.emptyList();
        String sql = """
            SELECT s.ID, s.name, s.slug, s.Country, s.SeriesType, s.finalStatus,
                   sa.CharacterName, sa.OrderIndex,
                   (SELECT MIN(e.ReleaseYear) FROM Episode e WHERE e.SeriesId = s.ID AND (e.IsHidden = 0 OR e.IsHidden IS NULL)) AS ReleaseYear,
                   (SELECT COUNT(*) FROM Episode e WHERE e.SeriesId = s.ID AND (e.IsHidden = 0 OR e.IsHidden IS NULL)) AS episodeCount,
                   (SELECT STRING_AGG(c.Name, ', ') FROM Categories c
                    JOIN SeriesCategories sc ON sc.CategoryID = c.ID
                    WHERE sc.SeriesID = s.ID) AS category
            FROM [dbo].[SeriesActors] sa
            JOIN [dbo].[Series] s ON sa.SeriesID = s.ID
            WHERE sa.ActorID = ? 
              AND (s.IsHidden = 0 OR s.IsHidden IS NULL)
              AND EXISTS (SELECT 1 FROM SeriesCategories sc_bl WHERE sc_bl.SeriesID = s.ID AND sc_bl.CategoryID IN (52, 63))
            ORDER BY s.name ASC
        """;
        try {
            return jdbcTemplate.query(sql, (rs, rowNum) -> {
                Map<String, Object> map = new HashMap<>();
                map.put("id", rs.getString("ID"));
                map.put("name", rs.getString("name"));
                map.put("slug", rs.getString("slug"));
                map.put("country", rs.getString("Country"));
                map.put("seriesType", rs.getString("SeriesType"));
                map.put("finalStatus", rs.getString("finalStatus"));
                map.put("characterName", rs.getString("CharacterName"));
                map.put("orderIndex", rs.getInt("OrderIndex"));
                int year = rs.getInt("ReleaseYear");
                if (!rs.wasNull()) map.put("releaseYear", year);
                map.put("episodeCount", rs.getInt("episodeCount"));
                map.put("category", rs.getString("category"));
                map.put("type", "series");
                return map;
            }, actorId.toString());
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }
}
