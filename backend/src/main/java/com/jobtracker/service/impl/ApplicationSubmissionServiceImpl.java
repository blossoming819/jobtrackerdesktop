package com.jobtracker.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jobtracker.entity.ApplicationSubmission;
import com.jobtracker.entity.JobApplication;
import com.jobtracker.mapper.ApplicationSubmissionMapper;
import com.jobtracker.service.ApplicationSubmissionService;
import com.jobtracker.service.OrganizationUnitService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class ApplicationSubmissionServiceImpl extends ServiceImpl<ApplicationSubmissionMapper, ApplicationSubmission> implements ApplicationSubmissionService {
    private final OrganizationUnitService organizationUnitService;
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

    @Override
    public ApplicationSubmission updateSubmission(String id, ApplicationSubmission changes) {
        ApplicationSubmission existing = getById(id);
        if (existing == null) throw new IllegalArgumentException("本次网申父记录不存在");
        existing.setOrganizationUnitId(changes.getOrganizationUnitId());
        organizationUnitService.applySubmissionOrganization(existing);
        existing.setRecruitmentType(changes.getRecruitmentType());
        // 地点、来源和投递时间属于具体志愿，父记录不保留也不再向新志愿复制。
        existing.setWorkLocation(null);
        existing.setSource(null);
        existing.setAppliedTime(null);
        existing.setRemark(changes.getRemark());
        updateById(existing);
        return existing;
    }

    private void copySharedFields(JobApplication application, ApplicationSubmission submission) {
        submission.setCompanyName(application.getCompanyName());
        submission.setRecruitmentType(application.getRecruitmentType());
        submission.setWorkLocation(null);
        submission.setSource(null);
        submission.setAppliedTime(null);
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
