package com.jobtracker.applymate.resumeparse.controller;

import com.jobtracker.applymate.resumeparse.dto.ResumeParseResponse;
import com.jobtracker.applymate.resumeparse.dto.ResumeParseRequest;
import com.jobtracker.applymate.resumeparse.dto.ResumeParseHistoryResponse;
import com.jobtracker.applymate.resumeparse.service.ResumeParseService;
import com.jobtracker.common.Result;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import org.springframework.http.MediaType;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/applymate/v1/resumes")
public class ResumeParseController {
    private final ResumeParseService resumeParseService;
    private final ObjectMapper objectMapper;

    @PostMapping("/{resumeId}/parse")
    public Result<ResumeParseResponse> parse(@PathVariable Long resumeId, @RequestBody(required = false) ResumeParseRequest request) {
        return Result.ok(resumeParseService.extract(resumeId, request == null ? new ResumeParseRequest(false, false) : request));
    }

    @org.springframework.web.bind.annotation.GetMapping("/{resumeId}/parse-records")
    public Result<List<ResumeParseHistoryResponse>> parseRecords(@PathVariable Long resumeId,
                                                                  @RequestParam(defaultValue = "10") int limit) {
        return Result.ok(resumeParseService.history(resumeId, limit));
    }

    @PostMapping(value = "/{resumeId}/parse/stream", produces = MediaType.APPLICATION_NDJSON_VALUE)
    public StreamingResponseBody parseStream(@PathVariable Long resumeId, @RequestBody(required = false) ResumeParseRequest request,
                                             @RequestParam(required = false) String provider) {
        ResumeParseRequest resolved = request == null ? new ResumeParseRequest(true, false) : request;
        return output -> {
            Object lock = new Object();
            java.util.function.BiConsumer<String, Object> emit = (type, value) -> {
                try {
                    byte[] line = (objectMapper.writeValueAsString(Map.of("type", type, "data", value)) + "\n").getBytes(StandardCharsets.UTF_8);
                    synchronized (lock) { output.write(line); output.flush(); }
                } catch (Exception exception) { throw new IllegalStateException("STREAM_WRITE_FAILED", exception); }
            };
            try {
                emit.accept("status", "模型已连接，正在生成档案草稿");
                ResumeParseResponse response = resumeParseService.extractStreaming(resumeId, resolved, provider, delta -> emit.accept("delta", delta));
                emit.accept("complete", response);
            } catch (Exception exception) {
                emit.accept("error", exception.getMessage() == null ? "AI_STREAM_FAILED" : exception.getMessage());
            }
        };
    }
}
