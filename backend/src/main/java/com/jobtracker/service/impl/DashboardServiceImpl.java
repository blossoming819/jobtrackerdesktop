package com.jobtracker.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jobtracker.entity.InterviewRecord;
import com.jobtracker.entity.JobApplication;
import com.jobtracker.service.DashboardService;
import com.jobtracker.service.InterviewRecordService;
import com.jobtracker.service.JobApplicationService;
import com.jobtracker.service.ReminderService;
import com.jobtracker.vo.ApplicationPeriodStatsVO;
import com.jobtracker.vo.DashboardVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DashboardServiceImpl implements DashboardService {
    private final JobApplicationService jobApplicationService;
    private final InterviewRecordService interviewRecordService;
    private final ReminderService reminderService;

    @Override
    public DashboardVO overview() {
        List<JobApplication> applications = jobApplicationService.list();
        DashboardVO vo = new DashboardVO();
        vo.setTotalApplications((long) applications.size());
        vo.setInterviewCount(interviewRecordService.count(new LambdaQueryWrapper<InterviewRecord>()));
        vo.setOfferCount(applications.stream().filter(item -> "Offer".equals(item.getCurrentStatus())).count());
        vo.setStatusCount(applications.stream().collect(Collectors.groupingBy(JobApplication::getCurrentStatus, LinkedHashMap::new, Collectors.counting())));
        vo.setCompanyCount(applications.stream().collect(Collectors.groupingBy(JobApplication::getCompanyName, LinkedHashMap::new, Collectors.counting())));
        vo.setApplicationPeriodStats(applicationPeriodStats(applications));
        vo.setDailyTrend(dailyTrend(applications));
        vo.setWeeklyTrend(weeklyTrend(applications));
        vo.setMonthlyTrend(monthlyTrend(applications));
        vo.setRecentApplications(applications.stream()
                .sorted((a, b) -> nullSafeTime(b).compareTo(nullSafeTime(a)))
                .limit(8)
                .toList());
        vo.setTodayReminders(reminderService.today());
        return vo;
    }

    private Map<String, ApplicationPeriodStatsVO> applicationPeriodStats(List<JobApplication> applications) {
        LocalDate today = LocalDate.now();
        LocalDate currentWeek = today.with(DayOfWeek.MONDAY);
        LocalDate currentMonth = today.withDayOfMonth(1);
        Map<String, ApplicationPeriodStatsVO> stats = new LinkedHashMap<>();
        stats.put("day", compare(applications, today, today.plusDays(1), today.minusDays(1), today));
        stats.put("week", compare(applications, currentWeek, currentWeek.plusWeeks(1), currentWeek.minusWeeks(1), currentWeek));
        stats.put("month", compare(applications, currentMonth, currentMonth.plusMonths(1), currentMonth.minusMonths(1), currentMonth));
        return stats;
    }

    private ApplicationPeriodStatsVO compare(
            List<JobApplication> applications,
            LocalDate currentStart,
            LocalDate currentEnd,
            LocalDate previousStart,
            LocalDate previousEnd
    ) {
        long current = countBetween(applications, currentStart, currentEnd);
        long previous = countBetween(applications, previousStart, previousEnd);
        return new ApplicationPeriodStatsVO(current, previous, current - previous);
    }

    private Map<String, Long> dailyTrend(List<JobApplication> applications) {
        LocalDate today = LocalDate.now();
        LocalDate start = today.minusDays(13);
        Map<String, Long> trend = new LinkedHashMap<>();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM-dd");
        for (int i = 0; i < 14; i++) {
            LocalDate day = start.plusDays(i);
            trend.put(day.format(formatter), countBetween(applications, day, day.plusDays(1)));
        }
        return trend;
    }

    private Map<String, Long> weeklyTrend(List<JobApplication> applications) {
        LocalDate currentMonday = LocalDate.now().with(DayOfWeek.MONDAY);
        LocalDate start = currentMonday.minusWeeks(11);
        Map<String, Long> trend = new LinkedHashMap<>();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM-dd");
        for (int i = 0; i < 12; i++) {
            LocalDate weekStart = start.plusWeeks(i);
            trend.put(weekStart.format(formatter), countBetween(applications, weekStart, weekStart.plusWeeks(1)));
        }
        return trend;
    }

    private Map<String, Long> monthlyTrend(List<JobApplication> applications) {
        YearMonth currentMonth = YearMonth.now();
        YearMonth start = currentMonth.minusMonths(11);
        Map<String, Long> trend = new LinkedHashMap<>();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM");
        for (int i = 0; i < 12; i++) {
            YearMonth month = start.plusMonths(i);
            LocalDate monthStart = month.atDay(1);
            trend.put(month.format(formatter), countBetween(applications, monthStart, monthStart.plusMonths(1)));
        }
        return trend;
    }

    private long countBetween(List<JobApplication> applications, LocalDate start, LocalDate end) {
        return applications.stream()
                .filter(item -> item.getAppliedTime() != null)
                .map(item -> item.getAppliedTime().toLocalDate())
                .filter(date -> !date.isBefore(start) && date.isBefore(end))
                .count();
    }

    private java.time.LocalDateTime nullSafeTime(JobApplication application) {
        if (application.getAppliedTime() != null) {
            return application.getAppliedTime();
        }
        if (application.getUpdatedTime() != null) {
            return application.getUpdatedTime();
        }
        return java.time.LocalDateTime.MIN;
    }
}
