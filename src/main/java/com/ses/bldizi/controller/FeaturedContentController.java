package com.ses.bldizi.controller;

import com.ses.bldizi.model.FeaturedTvItemDto;
import com.ses.bldizi.model.PageResponse;
import com.ses.bldizi.repository.MovieRepository;
import com.ses.bldizi.repository.SeriesRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/featured-content")
public class FeaturedContentController {

    private final SeriesRepository seriesRepository;
    private final MovieRepository movieRepository;

    public FeaturedContentController(SeriesRepository seriesRepository, MovieRepository movieRepository) {
        this.seriesRepository = seriesRepository;
        this.movieRepository = movieRepository;
    }

    @GetMapping("/tv")
    public ResponseEntity<PageResponse<FeaturedTvItemDto>> getFeaturedTv(
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {

        int offset = Math.max(0, (page - 1) * size);
        int totalElements = seriesRepository.countRecentBlEpisodes();
        int totalPages = (int) Math.ceil((double) totalElements / size);

        List<FeaturedTvItemDto> items = seriesRepository.findRecentBlEpisodes(offset, size);
        return ResponseEntity.ok(new PageResponse<>(items, totalPages, page, totalElements));
    }

    @GetMapping("/movies")
    public ResponseEntity<PageResponse<FeaturedTvItemDto>> getFeaturedMovies(
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {

        int offset = Math.max(0, (page - 1) * size);
        int totalElements = movieRepository.countRecentMoviesForFeatured();
        int totalPages = (int) Math.ceil((double) totalElements / size);

        List<FeaturedTvItemDto> items = movieRepository.findRecentMoviesForFeatured(offset, size);
        return ResponseEntity.ok(new PageResponse<>(items, totalPages, page, totalElements));
    }
}
