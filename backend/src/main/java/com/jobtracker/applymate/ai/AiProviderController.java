package com.jobtracker.applymate.ai;

import com.jobtracker.common.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/applymate/v1/ai")
public class AiProviderController {
    private final LlmProperties properties;
    @GetMapping("/providers")
    public Result<Map<String, ProviderStatus>> providers() {
        Map<String, ProviderStatus> result = properties.getProviders().entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> {
                    LlmProperties.Provider value = entry.getValue();
                    return new ProviderStatus(value.isEnabled(), value.getModel(), value.getCapabilities(), value.getApiKey() != null && !value.getApiKey().isBlank());
                }, (left, right) -> left, java.util.LinkedHashMap::new));
        return Result.ok(result);
    }

    public record ProviderStatus(boolean enabled, String model, LlmProperties.Capabilities capabilities, boolean credentialConfigured) { }
}
