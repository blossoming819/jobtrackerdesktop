package com.jobtracker.applymate.resumeparse.dto;

import java.time.LocalDateTime;

public record ResumeParseHistoryResponse(
        Long parseRecordId,
        Long resumeId,
        String status,
        int extractedLength,
        String extractor,
        String errorCode,
        LocalDateTime createdAt,
        Object draft
) { }
