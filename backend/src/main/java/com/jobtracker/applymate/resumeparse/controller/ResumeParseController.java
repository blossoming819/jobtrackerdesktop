package com.jobtracker.applymate.resumeparse.controller;

import com.jobtracker.applymate.resumeparse.dto.ResumeParseResponse;
import com.jobtracker.applymate.resumeparse.dto.ResumeParseRequest;
import com.jobtracker.applymate.resumeparse.service.ResumeParseService;
import com.jobtracker.common.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/applymate/v1/resumes")
public class ResumeParseController {
    private final ResumeParseService resumeParseService;

    @PostMapping("/{resumeId}/parse")
    public Result<ResumeParseResponse> parse(@PathVariable Long resumeId, @RequestBody(required = false) ResumeParseRequest request) {
        return Result.ok(resumeParseService.extract(resumeId, request == null ? new ResumeParseRequest(false, false) : request));
    }
}
