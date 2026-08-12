package com.jobtracker.applymate.profile.dto;

import java.time.LocalDateTime;

public record ProfileSnapshotResponse(
        Long id,
        int revision,
        String description,
        LocalDateTime createdAt
) {
}
