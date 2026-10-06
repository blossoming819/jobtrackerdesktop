package com.jobtracker.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobtracker.dto.ApplicationQueryDTO;
import com.jobtracker.entity.JobApplication;
import com.jobtracker.mapper.JobApplicationMapper;
import com.jobtracker.service.InterviewNoteService;
import com.jobtracker.service.InterviewRecordService;
import com.jobtracker.service.ReminderService;
import com.jobtracker.service.ResumeService;
import com.jobtracker.service.OrganizationUnitService;
import com.jobtracker.vo.ApplicationCompanyGroupVO;
import com.jobtracker.vo.ApplicationSubmissionVO;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JobApplicationServiceImplTest {

    @Test
    void submissionPageCollapsesPreferencesButKeepsIndependentApplicationsSeparate() {
        JobApplicationMapper mapper = mock(JobApplicationMapper.class);
        JobApplication first = application(1L, "中国建设银行");
        first.setSubmissionGroupId("ccb-2027");
        first.setPreferenceOrder(1);
        JobApplication second = application(2L, "中国建设银行");
        second.setSubmissionGroupId("ccb-2027");
        second.setPreferenceOrder(2);
        JobApplication independent = application(3L, "中国建设银行");
        independent.setSubmissionGroupId("independent-3");
        when(mapper.selectList(any())).thenReturn(List.of(first, second, independent));
        JobApplicationServiceImpl service = service(mapper);
        ApplicationQueryDTO query = new ApplicationQueryDTO();
        query.setPageNo(1L);
        query.setPageSize(10L);

        var result = service.pageApplicationSubmissions(query);

        assertThat(result.getTotal()).isEqualTo(2);
        ApplicationSubmissionVO submission = result.getRecords().get(0);
        assertThat(submission.isMultiPreference()).isTrue();
        assertThat(submission.getPreferences()).extracting(JobApplication::getId).containsExactly(1L, 2L);
        assertThat(result.getRecords().get(1).isMultiPreference()).isFalse();
    }

    @Test
    void groupedPageKeepsEveryCompanyTogetherBeforePagination() {
        JobApplicationMapper mapper = mock(JobApplicationMapper.class);
        when(mapper.selectList(any())).thenReturn(List.of(
                application(1L, "\u3000北京神舟航天\u200B"),
                application(2L, "北京神舟航天"),
                application(3L, " Example Co "),
                application(4L, "example co"),
                application(5L, "第三家公司"),
                application(6L, "第三家公司")
        ));
        JobApplicationServiceImpl service = service(mapper);
        ApplicationQueryDTO query = new ApplicationQueryDTO();
        query.setPageNo(1L);
        query.setPageSize(2L);

        var result = service.pageApplicationCompanies(query, "company");

        assertThat(result.getTotal()).isEqualTo(3);
        assertThat(result.getRecords()).hasSize(2);
        ApplicationCompanyGroupVO first = result.getRecords().get(0);
        assertThat(first.getCompanyName()).isEqualTo("北京神舟航天");
        assertThat(first.getChildren()).extracting(JobApplication::getId).containsExactly(1L, 2L);
        ApplicationCompanyGroupVO second = result.getRecords().get(1);
        assertThat(second.getCompanyName()).isEqualTo("Example Co");
        assertThat(second.getChildren()).extracting(JobApplication::getId).containsExactly(3L, 4L);
    }

    private JobApplicationServiceImpl service(JobApplicationMapper mapper) {
        JobApplicationServiceImpl service = new JobApplicationServiceImpl(
                mock(InterviewRecordService.class),
                mock(InterviewNoteService.class),
                mock(ResumeService.class),
                mock(ReminderService.class),
                new ObjectMapper(),
                mock(OrganizationUnitService.class)
        );
        ReflectionTestUtils.setField(service, "baseMapper", mapper);
        return service;
    }

    private JobApplication application(Long id, String companyName) {
        JobApplication application = new JobApplication();
        application.setId(id);
        application.setCompanyName(companyName);
        return application;
    }
}
