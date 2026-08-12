package com.jobtracker.applymate.profile.dto;

import java.time.LocalDateTime;

public record CandidateProfileSummary(
        String profileId,
        String name,
        String description,
        Long sourceResumeId,
        boolean defaultProfile,
        int revision,
        LocalDateTime updatedAt
) {
}
