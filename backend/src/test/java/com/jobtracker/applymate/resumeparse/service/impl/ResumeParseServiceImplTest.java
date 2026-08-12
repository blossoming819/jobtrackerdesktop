package com.jobtracker.applymate.resumeparse.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResumeParseServiceImplTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ResumeParseServiceImpl service = new ResumeParseServiceImpl(null, null, null, null, objectMapper);

    @Test
    void normalizesEducationChronologyAndInfersDegreeLadder() throws Exception {
        JsonNode input = objectMapper.readTree("""
                {
                  "basic":{"nameCn":"测试用户"},
                  "education":[
                    {"id":"edu_1","school":"硕士学校","startDate":"2027-07","expectedGraduation":"2024-09"},
                    {"id":"edu_2","school":"本科学校","startDate":"2020-09","expectedGraduation":"2024-06"}
                  ]
                }
                """);

        JsonNode result = normalize(input);

        assertEquals("2024-09", result.path("education").path(0).path("startDate").asText());
        assertEquals("2027-07", result.path("education").path(0).path("expectedGraduation").asText());
        assertEquals("MASTER", result.path("education").path(0).path("degree").asText());
        assertEquals("BACHELOR", result.path("education").path(1).path("degree").asText());
        assertEquals("FULL_TIME", result.path("education").path(0).path("studyMode").asText());
        assertEquals("FULL_TIME", result.path("education").path(1).path("studyMode").asText());
        assertTrue(result.path("fieldMeta").has("education.0.startDate"));
    }

    private JsonNode normalize(JsonNode input) throws Exception {
        Method method = ResumeParseServiceImpl.class.getDeclaredMethod("normalizeDraft", JsonNode.class);
        method.setAccessible(true);
        return (JsonNode) method.invoke(service, input);
    }
}
