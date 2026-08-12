package com.jobtracker.applymate.profile.dto;

public record CandidateProfileMetadataRequest(
        String name,
        String description,
        Long sourceResumeId
) {
}
