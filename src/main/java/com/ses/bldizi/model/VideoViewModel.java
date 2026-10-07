package com.ses.bldizi.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VideoViewModel {
    private String id;
    private String title;
    private String info;
    private String thumbnailUrl;
    private String videoUrl;
    private String country;
}
