package com.ses.bldizi.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/filters")
@SuppressWarnings({"SqlResolve", "SqlNoDataSourceInspection", "RedundantSuppression", "ConstantConditions", "unused"})
public class FilterController {

    private final JdbcTemplate jdbcTemplate;

    public FilterController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/options")
    public ResponseEntity<Map<String, Object>> getFilterOptions(
            @RequestParam(value = "type") String type) {
        Map<String, Object> options = new HashMap<>();

        // 1. Categories (excluding BL and GL since they have a separate toggle)
        String categorySql = """
            SELECT DISTINCT C.ID, C.Name
            FROM Categories C
            WHERE C.Name NOT IN ('BL', 'GL')
              AND (
                  EXISTS (
                      SELECT 1 FROM SeriesCategories SC
                      JOIN Series S ON S.ID = SC.SeriesID
                      WHERE SC.CategoryID = C.ID
                        AND S.IsHidden = 0
                        AND (S.SeriesType = 'BL' OR S.isBl = 1 OR EXISTS (SELECT 1 FROM SeriesCategories SC2 WHERE SC2.SeriesID = S.ID AND SC2.CategoryID IN (52, 63)))
                  )
                  OR EXISTS (
                      SELECT 1 FROM MovieCategories MC
                      JOIN Movie M ON M.ID = MC.MovieID
                      WHERE MC.CategoryID = C.ID
                        AND M.IsHidden = 0
                        AND (M.isBl = 1 OR EXISTS (SELECT 1 FROM MovieCategories MC2 WHERE MC2.MovieID = M.ID AND MC2.CategoryID IN (52, 63)))
                  )
              )
            ORDER BY C.Name
        """;

        List<Map<String, Object>> categories = jdbcTemplate.query(categorySql, (rs, _) -> {
            Map<String, Object> cat = new HashMap<>();
            cat.put("id", rs.getInt("ID"));
            cat.put("name", rs.getString("Name"));
            return cat;
        });
        options.put("categories", categories);

        // 2. Years
        String yearSql;
        if ("movie".equalsIgnoreCase(type)) {
            yearSql = """
                SELECT DISTINCT ReleaseYear as Year
                FROM Movie M
                WHERE M.IsHidden = 0
                  AND (M.isBl = 1 OR EXISTS (SELECT 1 FROM MovieCategories MC WHERE MC.MovieID = M.ID AND MC.CategoryID IN (52, 63)))
                  AND ReleaseYear IS NOT NULL AND ReleaseYear > 1900
                ORDER BY ReleaseYear DESC
            """;
        } else {
            yearSql = """
                SELECT DISTINCT YearVal FROM (
                    SELECT E.ReleaseYear as YearVal
                    FROM Episode E
                    JOIN Series S ON E.SeriesId = S.ID
                    WHERE S.IsHidden = 0
                      AND (S.SeriesType = 'BL' OR S.isBl = 1 OR EXISTS (SELECT 1 FROM SeriesCategories SC WHERE SC.SeriesID = S.ID AND SC.CategoryID IN (52, 63)))
                      AND E.ReleaseYear IS NOT NULL AND E.ReleaseYear > 1900
                    UNION
                    SELECT YEAR(S.uploadDate) as YearVal
                    FROM Series S
                    WHERE S.IsHidden = 0
                      AND (S.SeriesType = 'BL' OR S.isBl = 1 OR EXISTS (SELECT 1 FROM SeriesCategories SC WHERE SC.SeriesID = S.ID AND SC.CategoryID IN (52, 63)))
                      AND S.uploadDate IS NOT NULL AND YEAR(S.uploadDate) > 1900
                ) Y ORDER BY YearVal DESC
            """;
        }

        List<Integer> years = jdbcTemplate.queryForList(yearSql, Integer.class);
        options.put("years", years);

        // 3. Countries
        String countrySql;
        if ("movie".equalsIgnoreCase(type)) {
            countrySql = """
                SELECT DISTINCT Country
                FROM Movie M
                WHERE M.IsHidden = 0
                  AND (M.isBl = 1 OR EXISTS (SELECT 1 FROM MovieCategories MC WHERE MC.MovieID = M.ID AND MC.CategoryID IN (52, 63)))
                  AND Country IS NOT NULL AND Country != ''
                ORDER BY Country
            """;
        } else {
            countrySql = """
                SELECT DISTINCT Country
                FROM Series S
                WHERE S.IsHidden = 0
                  AND (S.SeriesType = 'BL' OR S.isBl = 1 OR EXISTS (SELECT 1 FROM SeriesCategories SC WHERE SC.SeriesID = S.ID AND SC.CategoryID IN (52, 63)))
                  AND Country IS NOT NULL AND Country != ''
                ORDER BY Country
            """;
        }

        List<String> rawCountries = jdbcTemplate.queryForList(countrySql, String.class);
        List<Map<String, String>> countries = rawCountries.stream()
                .map(code -> {
                    Map<String, String> c = new HashMap<>();
                    c.put("code", code);
                    c.put("name", mapCountryCodeToName(code));
                    return c;
                })
                .collect(Collectors.toList());
        options.put("countries", countries);

        return ResponseEntity.ok(options);
    }

    private String mapCountryCodeToName(String code) {
        if (code == null) return "Bilinmiyor";
        String lower = code.trim().toLowerCase();
        return switch (lower) {
            case "th", "tha" -> "Tayland";
            case "kr", "kor" -> "Güney Kore";
            case "jp", "jpn" -> "Japonya";
            case "tw", "twn" -> "Tayvan";
            case "ph", "phl" -> "Filipinler";
            case "cn", "chn" -> "Çin";
            case "hk", "hkg" -> "Hong Kong";
            case "vn", "vnm" -> "Vietnam";
            case "gb", "uk", "gbr" -> "Birleşik Krallık";
            case "us", "usa" -> "ABD";
            case "ca", "can" -> "Kanada";
            case "pl", "pol" -> "Polonya";
            case "es", "esp" -> "İspanya";
            case "fr", "fra" -> "Fransa";
            case "de", "deu" -> "Almanya";
            case "it", "ita" -> "İtalya";
            case "id", "idn" -> "Endonezya";
            case "my", "mys" -> "Malezya";
            case "sg", "sgp" -> "Singapur";
            case "tr", "tur" -> "Türkiye";
            default -> code.toUpperCase();
        };
    }
}
