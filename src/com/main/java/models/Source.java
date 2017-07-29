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
    "id",
    "name",
    "description",
    "url",
    "categoryDefault",
    "languageDefault",
    "countryDefault",
    "urlsToLogos",
    "sortBysAvailable"
})
public class Source {

    @JsonProperty("id")
    private String id;
    @JsonProperty("name")
    private String name;
    @JsonProperty("description")
    private String description;
    @JsonProperty("url")
    private String url;
    @JsonProperty("categoryDefault")
    private String category;
    @JsonProperty("languageDefault")
    private String language;
    @JsonProperty("countryDefault")
    private String country;
    @JsonProperty("urlsToLogos")
    private UrlsToLogos urlsToLogos;
    @JsonProperty("sortBysAvailable")
    private List<String> sortBysAvailable = null;
    @JsonIgnore
    private Map<String, Object> additionalProperties = new HashMap<String, Object>();

    @JsonProperty("id")
    public String getId() {
        return id;
    }

    @JsonProperty("id")
    public void setId(String id) {
        this.id = id;
    }

    @JsonProperty("name")
    public String getName() {
        return name;
    }

    @JsonProperty("name")
    public void setName(String name) {
        this.name = name;
    }

    @JsonProperty("description")
    public String getDescription() {
        return description;
    }

    @JsonProperty("description")
    public void setDescription(String description) {
        this.description = description;
    }

    @JsonProperty("url")
    public String getUrl() {
        return url;
    }

    @JsonProperty("url")
    public void setUrl(String url) {
        this.url = url;
    }

    @JsonProperty("categoryDefault")
    public String getCategory() {
        return category;
    }

    @JsonProperty("categoryDefault")
    public void setCategory(String category) {
        this.category = category;
    }

    @JsonProperty("languageDefault")
    public String getLanguage() {
        return language;
    }

    @JsonProperty("languageDefault")
    public void setLanguage(String language) {
        this.language = language;
    }

    @JsonProperty("countryDefault")
    public String getCountry() {
        return country;
    }

    @JsonProperty("countryDefault")
    public void setCountry(String country) {
        this.country = country;
    }

    @JsonProperty("urlsToLogos")
    public UrlsToLogos getUrlsToLogos() {
        return urlsToLogos;
    }

    @JsonProperty("urlsToLogos")
    public void setUrlsToLogos(UrlsToLogos urlsToLogos) {
        this.urlsToLogos = urlsToLogos;
    }

    @JsonProperty("sortBysAvailable")
    public List<String> getSortBysAvailable() {
        return sortBysAvailable;
    }

    @JsonProperty("sortBysAvailable")
    public void setSortBysAvailable(List<String> sortBysAvailable) {
        this.sortBysAvailable = sortBysAvailable;
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
