package com.ses.bldizi.controller;

import com.ses.bldizi.model.VideoViewModel;
import com.ses.bldizi.repository.MovieRepository;
import com.ses.bldizi.repository.SeriesRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/weekly-best")
public class WeeklyBestController {

    private final SeriesRepository seriesRepository;
    private final MovieRepository movieRepository;

    public WeeklyBestController(SeriesRepository seriesRepository, MovieRepository movieRepository) {
        this.seriesRepository = seriesRepository;
        this.movieRepository = movieRepository;
    }

    @GetMapping("/tv")
    public ResponseEntity<List<VideoViewModel>> getWeeklyBestSeries() {
        return ResponseEntity.ok(seriesRepository.findTop6BlSeriesByViews());
    }

    @GetMapping("/movies")
    public ResponseEntity<List<VideoViewModel>> getWeeklyBestMovies() {
        return ResponseEntity.ok(movieRepository.findTop6BlMoviesByViews());
    }
}
