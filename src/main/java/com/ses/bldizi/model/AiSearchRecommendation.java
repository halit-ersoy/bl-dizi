package com.ses.bldizi.model;

public class AiSearchRecommendation {
    private String id;
    private String name;
    private String type;
    private String category;
    private Integer year;
    private String slug;
    private String matchReason;

    public AiSearchRecommendation() {
    }

    public AiSearchRecommendation(String id, String name, String type, String category, Integer year, String slug, String matchReason) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.category = category;
        this.year = year;
        this.slug = slug;
        this.matchReason = matchReason;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public Integer getYear() {
        return year;
    }

    public void setYear(Integer year) {
        this.year = year;
    }

    public String getSlug() {
        return slug;
    }

    public void setSlug(String slug) {
        this.slug = slug;
    }

    public String getMatchReason() {
        return matchReason;
    }

    public void setMatchReason(String matchReason) {
        this.matchReason = matchReason;
    }
}
