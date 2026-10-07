package com.ses.bldizi.model;

import java.util.UUID;

public class CastMemberDto {
    private UUID actorId;
    private String name;
    private String characterName;
    private String photoUrl;
    private Integer tmdbId;
    private Integer tvmazeId;
    private int orderIndex;

    public CastMemberDto() {
    }

    public CastMemberDto(UUID actorId, String name, String characterName, String photoUrl, int orderIndex) {
        this.actorId = actorId;
        this.name = name;
        this.characterName = characterName;
        this.photoUrl = photoUrl;
        this.orderIndex = orderIndex;
    }

    public UUID getActorId() {
        return actorId;
    }

    public void setActorId(UUID actorId) {
        this.actorId = actorId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCharacterName() {
        return characterName;
    }

    public void setCharacterName(String characterName) {
        this.characterName = characterName;
    }

    public String getPhotoUrl() {
        return photoUrl;
    }

    public void setPhotoUrl(String photoUrl) {
        this.photoUrl = photoUrl;
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

    public int getOrderIndex() {
        return orderIndex;
    }

    public void setOrderIndex(int orderIndex) {
        this.orderIndex = orderIndex;
    }
}
