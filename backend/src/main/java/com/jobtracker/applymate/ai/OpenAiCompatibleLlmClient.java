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

@Service
@RequiredArgsConstructor
public class OpenAiCompatibleLlmClient {
    private final RestClient.Builder restClientBuilder;
    private final ObjectMapper objectMapper;
    private final LlmProperties properties;
    private final LlmProviderRouter router;

    public JsonNode generateJson(String route, String systemPrompt, String userPrompt, boolean requiresVision) {
        Set<String> attempted = new HashSet<>();
        while (true) {
            LlmProviderRouter.SelectedProvider selected = router.select(route, requiresVision, attempted);
            LlmProperties.Provider provider = properties.getProviders().get(selected.id());
            try {
            JsonNode response = restClientBuilder.baseUrl(trimTrailingSlash(selected.baseUrl()))
                    .defaultHeader("Authorization", "Bearer " + provider.getApiKey())
                    .build().post().uri("/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "model", selected.model(),
                            "messages", List.of(Map.of("role", "system", "content", systemPrompt), Map.of("role", "user", "content", userPrompt)),
                            "response_format", Map.of("type", "json_object"),
                            "temperature", 0.1))
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
            } catch (IllegalArgumentException exception) { throw exception;
            } catch (Exception exception) { router.recordRecoverableFailure(selected.id()); attempted.add(selected.id()); }
        }
    }
    private String trimTrailingSlash(String value) { return value.endsWith("/") ? value.substring(0, value.length() - 1) : value; }
}
