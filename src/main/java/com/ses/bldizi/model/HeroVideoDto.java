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
public class HeroVideoDto {
    private UUID id;
    private UUID referenceId;
    private String title;
    private String summary;
    private String category;
    private String videoUrl;
    private String thumbnailUrl;
    private String country;
    private String language;
    private Integer releaseYear;
    private Integer sortOrder;
    private boolean isImage;
}
