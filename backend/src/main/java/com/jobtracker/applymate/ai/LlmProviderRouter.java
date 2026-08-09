package com.jobtracker.applymate.ai;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class LlmProviderRouter {
    private final LlmProperties properties;
    private final Map<String, FailureState> failures = new ConcurrentHashMap<>();

    public SelectedProvider select(String task, boolean requiresVision) {
        return select(task, requiresVision, Set.of());
    }
    public SelectedProvider select(String task, boolean requiresVision, Set<String> excluded) {
        LlmProperties.Route route = properties.getRoutes().get(task);
        if (route == null || route.getCandidates().isEmpty()) throw new IllegalArgumentException("AI_ROUTE_NOT_CONFIGURED: " + task);
        for (String id : route.getCandidates()) {
            if (excluded.contains(id)) continue;
            LlmProperties.Provider provider = properties.getProviders().get(id);
            if (available(id, provider, requiresVision)) return new SelectedProvider(id, provider.getBaseUrl(), provider.getModel());
        }
        throw new IllegalArgumentException(requiresVision ? "VISION_UNAVAILABLE" : "AI_PROVIDER_UNAVAILABLE");
    }

    public void recordRecoverableFailure(String id) {
        failures.compute(id, (key, state) -> state == null ? new FailureState(1, Instant.now()) : state.next());
    }
    public void recordSuccess(String id) { failures.remove(id); }

    private boolean available(String id, LlmProperties.Provider provider, boolean requiresVision) {
        if (provider == null || !provider.isEnabled() || !StringUtils.hasText(provider.getApiKey()) || !StringUtils.hasText(provider.getBaseUrl()) || !StringUtils.hasText(provider.getModel())) return false;
        if (!provider.getCapabilities().isStructuredOutput() || (requiresVision && !provider.getCapabilities().isVisionInput())) return false;
        FailureState state = failures.get(id);
        return state == null || state.count < properties.getCircuitBreaker().getFailureThreshold() || Instant.now().isAfter(state.lastFailure.plusSeconds(properties.getCircuitBreaker().getOpenDurationSeconds()));
    }
    private record FailureState(int count, Instant lastFailure) { FailureState next() { return new FailureState(count + 1, Instant.now()); } }
    public record SelectedProvider(String id, String baseUrl, String model) { }
}
