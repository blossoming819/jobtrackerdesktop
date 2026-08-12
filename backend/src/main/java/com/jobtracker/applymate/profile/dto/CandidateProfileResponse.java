package com.jobtracker.applymate.profile.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.LocalDateTime;

public record CandidateProfileResponse(
        String profileId,
        String name,
        String description,
        Long sourceResumeId,
        boolean defaultProfile,
        String schemaVersion,
        int revision,
        JsonNode content,
        LocalDateTime updatedAt
) {
}
