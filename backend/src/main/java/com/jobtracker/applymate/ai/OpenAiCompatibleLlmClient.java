package com.jobtracker.applymate.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

@Service
@RequiredArgsConstructor
public class OpenAiCompatibleLlmClient {
    private final RestClient.Builder restClientBuilder;
    private final ObjectMapper objectMapper;
    private final LlmProperties properties;
    private final LlmProviderRouter router;

    public JsonNode generateJson(String route, String systemPrompt, String userPrompt, boolean requiresVision) {
        return generateJson(route, null, systemPrompt, userPrompt, requiresVision);
    }

    public JsonNode generateJson(String route, String providerId, String systemPrompt, String userPrompt, boolean requiresVision) {
        Set<String> attempted = new HashSet<>();
        while (true) {
            LlmProviderRouter.SelectedProvider selected = providerId == null || providerId.isBlank()
                    ? router.select(route, requiresVision, attempted)
                    : router.selectProvider(providerId, requiresVision);
            LlmProperties.Provider provider = properties.getProviders().get(selected.id());
            try {
            JsonNode response = restClientBuilder.baseUrl(trimTrailingSlash(selected.baseUrl()))
                    .defaultHeader("Authorization", "Bearer " + provider.getApiKey())
                    .build().post().uri("/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody(route, selected.id(), selected.model(), systemPrompt, userPrompt, false))
                    .retrieve().body(JsonNode.class);
            String content = response.path("choices").path(0).path("message").path("content").asText();
            JsonNode draft = objectMapper.readTree(content);
            if (!draft.isObject()) throw new IllegalArgumentException("AI_OUTPUT_INVALID");
                router.recordSuccess(selected.id());
                return draft;
            } catch (RestClientResponseException exception) {
                if (!exception.getStatusCode().is5xxServerError() && exception.getStatusCode().value() != 429) throw new IllegalArgumentException("AI_REQUEST_FAILED: " + exception.getStatusCode().value());
                router.recordRecoverableFailure(selected.id());
                attempted.add(selected.id());
                if (providerId != null && !providerId.isBlank()) throw new IllegalArgumentException("AI_PROVIDER_REQUEST_FAILED: " + providerId);
            } catch (IllegalArgumentException exception) { throw exception;
            } catch (Exception exception) {
                router.recordRecoverableFailure(selected.id()); attempted.add(selected.id());
                if (providerId != null && !providerId.isBlank()) throw new IllegalArgumentException("AI_PROVIDER_REQUEST_FAILED: " + providerId);
            }
        }
    }

    public JsonNode generateJsonStreaming(String route, String providerId, String systemPrompt, String userPrompt, boolean requiresVision, Consumer<String> onDelta) {
        LlmProviderRouter.SelectedProvider selected = providerId == null || providerId.isBlank()
                ? router.select(route, requiresVision, Set.of())
                : router.selectProvider(providerId, requiresVision);
        LlmProperties.Provider provider = properties.getProviders().get(selected.id());
        StringBuilder content = new StringBuilder();
        java.util.concurrent.atomic.AtomicReference<String> finishReason = new java.util.concurrent.atomic.AtomicReference<>();
        try {
            restClientBuilder.baseUrl(trimTrailingSlash(selected.baseUrl()))
                    .defaultHeader("Authorization", "Bearer " + provider.getApiKey())
                    .build().post().uri("/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody(route, selected.id(), selected.model(), systemPrompt, userPrompt, true))
                    .exchange((request, response) -> {
                        if (!response.getStatusCode().is2xxSuccessful()) {
                            String detail = new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8);
                            throw new IllegalArgumentException("AI_REQUEST_FAILED: HTTP " + response.getStatusCode().value() + " " + safeDetail(detail));
                        }
                        try (BufferedReader reader = new BufferedReader(new InputStreamReader(response.getBody(), StandardCharsets.UTF_8))) {
                            String line;
                            while ((line = reader.readLine()) != null) {
                                if (!line.startsWith("data:")) continue;
                                String data = line.substring(5).trim();
                                if (data.isEmpty() || "[DONE]".equals(data)) continue;
                                JsonNode chunk = objectMapper.readTree(data);
                                String reason = chunk.path("choices").path(0).path("finish_reason").asText("");
                                if (!reason.isBlank()) finishReason.set(reason);
                                String delta = chunk.path("choices").path(0).path("delta").path("content").asText("");
                                if (!delta.isEmpty()) {
                                    content.append(delta);
                                    onDelta.accept(delta);
                                }
                            }
                        }
                        return null;
                    });
            if ("length".equalsIgnoreCase(finishReason.get())) throw new IllegalArgumentException("AI_OUTPUT_TRUNCATED: 模型达到输出长度上限，请提高 resume-parse.max-output-tokens");
            JsonNode draft = objectMapper.readTree(stripCodeFence(content.toString()));
            if (!draft.isObject()) throw new IllegalArgumentException("AI_OUTPUT_INVALID");
            router.recordSuccess(selected.id());
            return draft;
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            router.recordRecoverableFailure(selected.id());
            throw new IllegalArgumentException("AI_STREAM_FAILED: " + safeDetail(exception.getMessage()));
        }
    }

    private String stripCodeFence(String value) {
        String trimmed = value == null ? "" : value.trim();
        if (!trimmed.startsWith("```")) return trimmed;
        int firstBreak = trimmed.indexOf('\n');
        int lastFence = trimmed.lastIndexOf("```");
        return firstBreak >= 0 && lastFence > firstBreak ? trimmed.substring(firstBreak + 1, lastFence).trim() : trimmed;
    }

    private Map<String, Object> requestBody(String route, String providerId, String model, String systemPrompt, String userPrompt, boolean streaming) {
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("model", model);
        body.put("messages", List.of(Map.of("role", "system", "content", systemPrompt), Map.of("role", "user", "content", userPrompt)));
        body.put("response_format", Map.of("type", "json_object"));
        body.put("temperature", 0.1);
        body.put("stream", streaming);
        LlmProperties.Route routeConfig = properties.getRoutes().get(route);
        if (routeConfig != null && routeConfig.getMaxOutputTokens() > 0) body.put("max_tokens", routeConfig.getMaxOutputTokens());
        if ("deepseek".equalsIgnoreCase(providerId)) body.put("thinking", Map.of("type", "disabled"));
        return body;
    }

    private String safeDetail(String value) {
        if (value == null || value.isBlank()) return "no detail";
        String compact = value.replaceAll("\\s+", " ").trim();
        return compact.length() > 500 ? compact.substring(0, 500) : compact;
    }
    private String trimTrailingSlash(String value) { return value.endsWith("/") ? value.substring(0, value.length() - 1) : value; }
}
