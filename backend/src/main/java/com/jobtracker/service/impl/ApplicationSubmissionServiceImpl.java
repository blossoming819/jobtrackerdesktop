package com.jobtracker.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jobtracker.entity.ApplicationSubmission;
import com.jobtracker.entity.JobApplication;
import com.jobtracker.mapper.ApplicationSubmissionMapper;
import com.jobtracker.service.ApplicationSubmissionService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class ApplicationSubmissionServiceImpl extends ServiceImpl<ApplicationSubmissionMapper, ApplicationSubmission> implements ApplicationSubmissionService {
    @Override
    public ApplicationSubmission ensureFrom(JobApplication application) {
        if (!StringUtils.hasText(application.getSubmissionGroupId())) {
            throw new IllegalArgumentException("投递记录缺少父记录标识");
        }
        ApplicationSubmission existing = getById(application.getSubmissionGroupId());
        if (existing != null) return existing;
        ApplicationSubmission submission = new ApplicationSubmission();
        submission.setId(application.getSubmissionGroupId());
        copySharedFields(application, submission);
        save(submission);
        return submission;
    }

    @Override
    public ApplicationSubmission syncFrom(JobApplication application) {
        ApplicationSubmission submission = ensureFrom(application);
        copySharedFields(application, submission);
        updateById(submission);
        return submission;
    }

    private void copySharedFields(JobApplication application, ApplicationSubmission submission) {
        submission.setCompanyName(application.getCompanyName());
        submission.setRecruitmentType(application.getRecruitmentType());
        submission.setWorkLocation(application.getWorkLocation());
        submission.setSource(application.getSource());
        submission.setAppliedTime(application.getAppliedTime());
        submission.setSubmissionOrganizationId(application.getSubmissionOrganizationId());
        submission.setEmployerOrganizationId(application.getEmployerOrganizationId());
        submission.setOrganizationUnitId(application.getOrganizationUnitId());
        submission.setOrganizationPathSnapshot(application.getOrganizationPathSnapshot());
        submission.setEmployerNameSnapshot(application.getEmployerNameSnapshot());
        submission.setGroupNameSnapshot(application.getGroupNameSnapshot());
        submission.setRemark(application.getRemark());
    }

    @Override
    public void applyDefaults(ApplicationSubmission submission, JobApplication application) {
        if (!StringUtils.hasText(application.getCompanyName())) application.setCompanyName(submission.getCompanyName());
        if (!StringUtils.hasText(application.getRecruitmentType())) application.setRecruitmentType(submission.getRecruitmentType());
        if (!StringUtils.hasText(application.getWorkLocation())) application.setWorkLocation(submission.getWorkLocation());
        if (!StringUtils.hasText(application.getSource())) application.setSource(submission.getSource());
        if (application.getAppliedTime() == null) application.setAppliedTime(submission.getAppliedTime());
        if (application.getOrganizationUnitId() == null) {
            application.setSubmissionOrganizationId(submission.getSubmissionOrganizationId());
            application.setEmployerOrganizationId(submission.getEmployerOrganizationId());
            application.setOrganizationUnitId(submission.getOrganizationUnitId());
            application.setOrganizationPathSnapshot(submission.getOrganizationPathSnapshot());
            application.setEmployerNameSnapshot(submission.getEmployerNameSnapshot());
            application.setGroupNameSnapshot(submission.getGroupNameSnapshot());
        }
    }
}
