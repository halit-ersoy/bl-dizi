package com.ses.bldizi.model;

import java.time.Instant;
import java.util.UUID;

public class Actor {
    private UUID id;
    private String name;
    private String photoUrl;
    private String remotePhotoUrl;
    private Integer tmdbId;
    private Integer tvmazeId;
    private String biography;
    private String birthday;
    private String deathday;
    private String placeOfBirth;
    private String knownFor;
    private Integer gender;
    private Instant createdAt;

    public Actor() {
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPhotoUrl() {
        return photoUrl;
    }

    public void setPhotoUrl(String photoUrl) {
        this.photoUrl = photoUrl;
    }

    public String getRemotePhotoUrl() {
        return remotePhotoUrl;
    }

    public void setRemotePhotoUrl(String remotePhotoUrl) {
        this.remotePhotoUrl = remotePhotoUrl;
    }

    public Integer getTmdbId() {
        return tmdbId;
    }

    public void setTmdbId(Integer tmdbId) {
        this.tmdbId = tmdbId;
    }

    public Integer getTvmazeId() {
        return tvmazeId;
    }

    public void setTvmazeId(Integer tvmazeId) {
        this.tvmazeId = tvmazeId;
    }

    public String getBiography() {
        return biography;
    }

    public void setBiography(String biography) {
        this.biography = biography;
    }

    public String getBirthday() {
        return birthday;
    }

    public void setBirthday(String birthday) {
        this.birthday = birthday;
    }

    public String getDeathday() {
        return deathday;
    }

    public void setDeathday(String deathday) {
        this.deathday = deathday;
    }

    public String getPlaceOfBirth() {
        return placeOfBirth;
    }

    public void setPlaceOfBirth(String placeOfBirth) {
        this.placeOfBirth = placeOfBirth;
    }

    public String getKnownFor() {
        return knownFor;
    }

    public void setKnownFor(String knownFor) {
        this.knownFor = knownFor;
    }

    public Integer getGender() {
        return gender;
    }

    public void setGender(Integer gender) {
        this.gender = gender;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
