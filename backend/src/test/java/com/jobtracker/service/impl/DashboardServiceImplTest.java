package com.jobtracker.service.impl;

import com.jobtracker.entity.JobApplication;
import com.jobtracker.service.InterviewRecordService;
import com.jobtracker.service.JobApplicationService;
import com.jobtracker.service.ReminderService;
import com.jobtracker.vo.DashboardVO;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DashboardServiceImplTest {

    @Test
    void overviewIncludesPeriodComparisonsAndDayWeekMonthTrends() {
        JobApplicationService applicationService = mock(JobApplicationService.class);
        InterviewRecordService interviewRecordService = mock(InterviewRecordService.class);
        ReminderService reminderService = mock(ReminderService.class);
        LocalDate today = LocalDate.now();
        when(applicationService.list()).thenReturn(List.of(
                application(1L, "A", "group-1", 1, today),
                application(2L, "A", "group-1", 2, today),
                application(3L, "C", null, null, today.minusDays(1))
        ));
        when(reminderService.today()).thenReturn(List.of());

        DashboardVO result = new DashboardServiceImpl(
                applicationService,
                interviewRecordService,
                reminderService
        ).overview();

        assertThat(result.getApplicationPeriodStats().get("day").getCurrentCount()).isEqualTo(2);
        assertThat(result.getApplicationPeriodStats().get("day").getPreviousCount()).isEqualTo(1);
        assertThat(result.getApplicationPeriodStats().get("day").getChange()).isEqualTo(1);
        assertThat(result.getTotalApplications()).isEqualTo(3);
        assertThat(result.getTotalCompanies()).isEqualTo(2);
        assertThat(result.getTotalSubmissions()).isEqualTo(2);
        assertThat(result.getDailyTrend()).hasSize(14).containsValue(2L);
        assertThat(result.getWeeklyTrend()).hasSize(12);
        assertThat(result.getMonthlyTrend()).hasSize(12);
    }

    private JobApplication application(Long id, String companyName, String submissionGroupId, Integer preferenceOrder, LocalDate appliedDate) {
        JobApplication application = new JobApplication();
        application.setId(id);
        application.setCompanyName(companyName);
        application.setSubmissionGroupId(submissionGroupId);
        application.setPreferenceOrder(preferenceOrder);
        application.setCurrentStatus("已投递");
        application.setAppliedTime(appliedDate.atTime(10, 0));
        return application;
    }
}
