package com.jobtracker.applymate.ai;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
@ConfigurationProperties(prefix = "applymate.llm")
public class LlmProperties {
    private CircuitBreaker circuitBreaker = new CircuitBreaker();
    private Map<String, Provider> providers = new LinkedHashMap<>();
    private Map<String, Route> routes = new LinkedHashMap<>();

    @Data public static class CircuitBreaker { private int failureThreshold = 3; private int openDurationSeconds = 60; }
    @Data public static class Route { private List<String> candidates = List.of(); }
    @Data public static class Provider {
        private boolean enabled;
        private String baseUrl;
        private String apiKey;
        private String model;
        private Capabilities capabilities = new Capabilities();
    }
    @Data public static class Capabilities {
        private boolean structuredOutput;
        private boolean visionInput;
        private boolean jsonSchema;
        private boolean streaming;
    }
}
