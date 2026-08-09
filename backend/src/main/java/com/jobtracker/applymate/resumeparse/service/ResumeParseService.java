package com.jobtracker.applymate.resumeparse.service;

import com.jobtracker.applymate.resumeparse.dto.ResumeParseResponse;

public interface ResumeParseService {
    ResumeParseResponse extract(Long resumeId);
}
