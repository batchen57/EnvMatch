package com.envmatch.service;

import com.fasterxml.jackson.databind.JsonNode;

public record AiAnalysisResponse(JsonNode result, JsonNode usage, String error) {
}
