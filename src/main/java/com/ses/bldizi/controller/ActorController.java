package com.ses.bldizi.controller;

import com.ses.bldizi.model.Actor;
import com.ses.bldizi.repository.ActorRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/actor")
public class ActorController {

    private final ActorRepository actorRepository;

    public ActorController(ActorRepository actorRepository) {
        this.actorRepository = actorRepository;
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getActorDetails(@PathVariable("id") UUID id) {
        Actor actor = actorRepository.findById(id);
        if (actor == null) {
            return ResponseEntity.notFound().build();
        }

        String biography = actor.getBiography();
        String birthday = actor.getBirthday();
        String deathday = actor.getDeathday();
        String placeOfBirth = actor.getPlaceOfBirth();
        String knownFor = actor.getKnownFor();
        Integer gender = actor.getGender();

        if (knownFor == null || knownFor.trim().isEmpty()) {
            knownFor = "Oyuncu";
        }

        List<Map<String, Object>> movies = actorRepository.getMoviesForActor(id);
        List<Map<String, Object>> series = actorRepository.getSeriesForActor(id);

        List<Map<String, Object>> allProductions = new ArrayList<>();
        allProductions.addAll(movies);
        allProductions.addAll(series);

        allProductions.sort((a, b) -> {
            Integer yearA = a.get("releaseYear") instanceof Number ? ((Number) a.get("releaseYear")).intValue() : 0;
            Integer yearB = b.get("releaseYear") instanceof Number ? ((Number) b.get("releaseYear")).intValue() : 0;
            return yearB.compareTo(yearA);
        });

        Map<String, Object> response = new HashMap<>();
        response.put("id", actor.getId().toString());
        response.put("name", actor.getName());

        String photoUrl = actor.getPhotoUrl();
        if (photoUrl == null || photoUrl.isEmpty()) {
            photoUrl = actor.getRemotePhotoUrl();
        }
        response.put("photoUrl", photoUrl != null ? photoUrl : "/media/actor/" + actor.getId());

        response.put("biography", biography != null && !biography.trim().isEmpty() ? biography.trim() : null);
        response.put("birthday", birthday);
        response.put("deathday", deathday);
        response.put("placeOfBirth", placeOfBirth);
        response.put("knownFor", knownFor);
        response.put("gender", gender);

        response.put("movies", movies);
        response.put("series", series);
        response.put("productions", allProductions);
        response.put("totalProductions", allProductions.size());

        return ResponseEntity.ok(response);
    }
}
