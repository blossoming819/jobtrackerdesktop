package com.jobtracker.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.jobtracker.entity.ApplicationSubmission;
import com.jobtracker.entity.JobApplication;

public interface ApplicationSubmissionService extends IService<ApplicationSubmission> {
    ApplicationSubmission ensureFrom(JobApplication application);
    ApplicationSubmission syncFrom(JobApplication application);
    ApplicationSubmission updateSubmission(String id, ApplicationSubmission submission);
    void applyDefaults(ApplicationSubmission submission, JobApplication application);
}
