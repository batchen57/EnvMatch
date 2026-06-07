package com.envmatch.model;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "ai_models")
public class AIModel {
    @Id
    @Column(length = 36)
    private String id;
    private String name;
    private String identifier;
    private String provider;
    @Column(columnDefinition = "TEXT")
    private String apiKey;
    @Column(columnDefinition = "TEXT")
    private String baseUrl;
    @Column(columnDefinition = "TEXT")
    private String description;
    @Convert(converter = JsonNodeConverter.class)
    @Column(columnDefinition = "TEXT")
    private JsonNode capabilities;
    private String isDefault = "false";
    private Double sortOrder = 0.0;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        if (id == null || id.isBlank()) id = UUID.randomUUID().toString();
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getIdentifier() { return identifier; }
    public void setIdentifier(String identifier) { this.identifier = identifier; }
    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }
    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public JsonNode getCapabilities() { return capabilities; }
    public void setCapabilities(JsonNode capabilities) { this.capabilities = capabilities; }
    public String getIsDefault() { return isDefault; }
    public void setIsDefault(String isDefault) { this.isDefault = isDefault; }
    public Double getSortOrder() { return sortOrder; }
    public void setSortOrder(Double sortOrder) { this.sortOrder = sortOrder; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
