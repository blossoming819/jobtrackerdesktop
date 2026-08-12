package com.jobtracker.applymate.resumeparse.service;

import com.jobtracker.applymate.resumeparse.dto.ResumeParseRequest;
import com.jobtracker.applymate.resumeparse.dto.ResumeParseResponse;
import com.jobtracker.applymate.resumeparse.dto.ResumeParseHistoryResponse;
import java.util.List;
import java.util.function.Consumer;

public interface ResumeParseService {
    ResumeParseResponse extract(Long resumeId, ResumeParseRequest request);
    ResumeParseResponse extractStreaming(Long resumeId, ResumeParseRequest request, String providerId, Consumer<String> onDelta);
    List<ResumeParseHistoryResponse> history(Long resumeId, int limit);
}
