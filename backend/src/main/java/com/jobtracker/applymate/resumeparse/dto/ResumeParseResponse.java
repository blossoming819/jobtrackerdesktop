package com.jobtracker.applymate.resumeparse.dto;

public record ResumeParseResponse(Long parseRecordId, Long resumeId, String status, int extractedLength, boolean visionRecommended) {
}
