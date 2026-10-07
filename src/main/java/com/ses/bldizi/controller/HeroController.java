package com.ses.bldizi.controller;

import com.ses.bldizi.model.HeroVideoDto;
import com.ses.bldizi.repository.HeroRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/hero")
public class HeroController {

    private final HeroRepository heroRepository;

    public HeroController(HeroRepository heroRepository) {
        this.heroRepository = heroRepository;
    }

    @GetMapping("/videos")
    public ResponseEntity<List<HeroVideoDto>> getHeroVideos() {
        return ResponseEntity.ok(heroRepository.getHeroVideos());
    }
}
