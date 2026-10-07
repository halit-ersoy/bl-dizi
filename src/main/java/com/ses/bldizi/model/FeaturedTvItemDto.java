package com.ses.bldizi.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeaturedTvItemDto {
    private UUID id;
    private UUID seriesId;
    private String title;
    private Integer season;
    private Integer episode;
    private boolean isNew;
    private boolean isFinal;
    private String image;
    private String language;
    private String country;
    private Integer finalStatus;
    private String seriesType;
    private String slug;
}
