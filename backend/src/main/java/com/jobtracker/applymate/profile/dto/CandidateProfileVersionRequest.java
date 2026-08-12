package com.jobtracker.applymate.profile.dto;

import com.fasterxml.jackson.databind.JsonNode;

public record CandidateProfileVersionRequest(
        String name,
        String description,
        Long sourceResumeId,
        String copyFromProfileId,
        JsonNode content
) {
}
