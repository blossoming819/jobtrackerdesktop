package com.jobtracker.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.jobtracker.entity.ApplicationSubmission;
import com.jobtracker.entity.JobApplication;

public interface ApplicationSubmissionService extends IService<ApplicationSubmission> {
    ApplicationSubmission ensureFrom(JobApplication application);
    ApplicationSubmission syncFrom(JobApplication application);
    void applyDefaults(ApplicationSubmission submission, JobApplication application);
}
