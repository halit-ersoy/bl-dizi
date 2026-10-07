package com.ses.bldizi.controller;

import com.ses.bldizi.model.PageResponse;
import com.ses.bldizi.model.VideoViewModel;
import com.ses.bldizi.repository.MovieRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/movies")
public class MovieController {

    private final MovieRepository movieRepository;

    public MovieController(MovieRepository movieRepository) {
        this.movieRepository = movieRepository;
    }

    @GetMapping("/recent")
    public ResponseEntity<PageResponse<VideoViewModel>> getRecentMoviesPaged(
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "18") int size) {

        int offset = Math.max(0, (page - 1) * size);
        List<VideoViewModel> movies = movieRepository.findRecentMoviesPaged(offset, size);
        int totalElements = movieRepository.countAllMovies();
        int totalPages = (int) Math.ceil((double) totalElements / size);

        return ResponseEntity.ok(new PageResponse<>(movies, totalPages, page, totalElements));
    }

    @GetMapping("/search")
    public ResponseEntity<PageResponse<VideoViewModel>> searchMoviesPaged(
            @RequestParam(value = "query", defaultValue = "") String query,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "18") int size) {

        if (query == null || query.trim().isEmpty()) {
            return getRecentMoviesPaged(page, size);
        }

        int offset = Math.max(0, (page - 1) * size);
        List<VideoViewModel> movies = movieRepository.searchMoviesPaged(query, offset, size);
        int totalElements = movieRepository.countMoviesBySearch(query);
        int totalPages = (int) Math.ceil((double) totalElements / size);

        return ResponseEntity.ok(new PageResponse<>(movies, totalPages, page, totalElements));
    }
}
