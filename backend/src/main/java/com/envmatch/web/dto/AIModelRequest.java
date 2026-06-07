package com.envmatch.web.dto;

import com.fasterxml.jackson.databind.JsonNode;

public record AIModelRequest(
        String name,
        String identifier,
        String provider,
        String apiKey,
        String baseUrl,
        String description,
        JsonNode capabilities,
        String isDefault,
        Double sortOrder
) {
    public String apiKeyOrEmpty() {
        return apiKey == null ? "" : apiKey;
    }

    public String baseUrlOrEmpty() {
        return baseUrl == null ? "" : baseUrl;
    }

    public String descriptionOrEmpty() {
        return description == null ? "" : description;
    }

    public String isDefaultOrFalse() {
        return isDefault == null ? "false" : isDefault;
    }

    public double sortOrderOrZero() {
        return sortOrder == null ? 0.0 : sortOrder;
    }
}
