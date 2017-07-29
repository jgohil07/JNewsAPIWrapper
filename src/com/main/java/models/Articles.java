package com.main.java.models;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "status",
    "sourceDefault",
    "sortByDefault",
    "articles"
})
public class Articles {

    @JsonProperty("status")
    private String status;
    @JsonProperty("sourceDefault")
    private String source;
    @JsonProperty("sortByDefault")
    private String sortBy;
    @JsonProperty("articles")
    private List<Article> articles = null;
    @JsonIgnore
    private Map<String, Object> additionalProperties = new HashMap<String, Object>();

    @JsonProperty("status")
    public String getStatus() {
        return status;
    }

    @JsonProperty("status")
    public void setStatus(String status) {
        this.status = status;
    }

    @JsonProperty("sourceDefault")
    public String getSource() {
        return source;
    }

    @JsonProperty("sourceDefault")
    public void setSource(String source) {
        this.source = source;
    }

    @JsonProperty("sortByDefault")
    public String getSortBy() {
        return sortBy;
    }

    @JsonProperty("sortByDefault")
    public void setSortBy(String sortBy) {
        this.sortBy = sortBy;
    }

    @JsonProperty("articles")
    public List<Article> getArticles() {
        return articles;
    }

    @JsonProperty("articles")
    public void setArticles(List<Article> articles) {
        this.articles = articles;
    }

    @JsonAnyGetter
    public Map<String, Object> getAdditionalProperties() {
        return this.additionalProperties;
    }

    @JsonAnySetter
    public void setAdditionalProperty(String name, Object value) {
        this.additionalProperties.put(name, value);
    }

}
