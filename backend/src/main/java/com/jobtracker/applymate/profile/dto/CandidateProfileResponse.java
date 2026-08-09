package com.jobtracker.applymate.profile.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.LocalDateTime;

public record CandidateProfileResponse(
        String profileId,
        String schemaVersion,
        int revision,
        JsonNode content,
        LocalDateTime updatedAt
) {
}
