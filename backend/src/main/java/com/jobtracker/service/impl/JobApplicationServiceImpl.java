package com.jobtracker.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobtracker.dto.ApplicationQueryDTO;
import com.jobtracker.entity.InterviewNote;
import com.jobtracker.entity.InterviewRecord;
import com.jobtracker.entity.JobApplication;
import com.jobtracker.entity.Reminder;
import com.jobtracker.entity.Resume;
import com.jobtracker.mapper.JobApplicationMapper;
import com.jobtracker.service.InterviewNoteService;
import com.jobtracker.service.InterviewRecordService;
import com.jobtracker.service.JobApplicationService;
import com.jobtracker.service.ReminderService;
import com.jobtracker.service.ResumeService;
import com.jobtracker.vo.ApplicationDetailVO;
import com.jobtracker.vo.ApplicationCompanyGroupVO;
import com.jobtracker.vo.ApplicationSubmissionVO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class JobApplicationServiceImpl extends ServiceImpl<JobApplicationMapper, JobApplication> implements JobApplicationService {
    private static final String EFFECTIVE_APPLIED_TIME_SQL = "COALESCE(applied_time, "
            + "(SELECT MAX(sibling.applied_time) FROM job_application sibling "
            + "WHERE sibling.submission_group_id = job_application.submission_group_id AND sibling.deleted = 0), "
            + "updated_time)";
    private static final List<String> DEFAULT_STATUS_OPTIONS = List.of(
            "收藏", "待投递", "已投递", "笔试", "面试中", "一面", "二面", "三面", "四面", "主管面", "HR 面", "Offer", "淘汰"
    );
    private static final List<String> DEFAULT_POSITION_TYPE_OPTIONS = List.of(
            "Java 后端", "Go 后端", "C++ 后端", "算法工程师", "AI / 大模型", "数据开发", "数据分析",
            "前端开发", "客户端开发", "测试开发", "产品经理", "运营", "银行金融科技", "国企管培 / 综合岗", "硬件 / 嵌入式"
    );
    private static final List<String> DEFAULT_RESUME_CATEGORY_OPTIONS = List.of(
            "Java 简历", "算法简历", "AI 简历", "前端简历", "国企简历", "通用简历"
    );

    private final InterviewRecordService interviewRecordService;
    private final InterviewNoteService interviewNoteService;
    private final ResumeService resumeService;
    private final ReminderService reminderService;
    private final ObjectMapper objectMapper;

    @Override
    public Page<JobApplication> pageApplications(ApplicationQueryDTO query) {
        QueryWrapper<JobApplication> wrapper = applicationQuery(query);
        return page(new Page<>(query.getPageNo(), query.getPageSize()), wrapper);
    }

    @Override
    public Page<ApplicationSubmissionVO> pageApplicationSubmissions(ApplicationQueryDTO query) {
        List<ApplicationSubmissionVO> submissions = groupSubmissionApplications(list(applicationQuery(query)));
        long pageNo = Math.max(query.getPageNo(), 1L);
        long pageSize = Math.max(query.getPageSize(), 1L);
        int fromIndex = (int) Math.min((pageNo - 1) * pageSize, submissions.size());
        int toIndex = (int) Math.min(fromIndex + pageSize, submissions.size());
        Page<ApplicationSubmissionVO> result = new Page<>(pageNo, pageSize, submissions.size());
        result.setRecords(submissions.subList(fromIndex, toIndex));
        return result;
    }

    @Override
    public Page<ApplicationCompanyGroupVO> pageApplicationCompanies(ApplicationQueryDTO query) {
        List<JobApplication> applications = list(applicationQuery(query));
        Map<String, ApplicationCompanyGroupVO> groups = new LinkedHashMap<>();
        for (JobApplication application : applications) {
            String companyName = normalizeCompanyName(application.getCompanyName());
            String companyKey = companyName.toLowerCase(Locale.ROOT);
            ApplicationCompanyGroupVO group = groups.computeIfAbsent(companyKey, ignored -> {
                ApplicationCompanyGroupVO created = new ApplicationCompanyGroupVO();
                created.setCompanyName(companyName);
                return created;
            });
            group.getChildren().add(application);
        }

        List<ApplicationCompanyGroupVO> allGroups = new ArrayList<>(groups.values());
        allGroups.forEach(group -> group.setChildren(new ArrayList<>(groupSubmissionApplications(group.getChildren()))));
        long pageNo = Math.max(query.getPageNo(), 1L);
        long pageSize = Math.max(query.getPageSize(), 1L);
        int fromIndex = (int) Math.min((pageNo - 1) * pageSize, allGroups.size());
        int toIndex = (int) Math.min(fromIndex + pageSize, allGroups.size());
        Page<ApplicationCompanyGroupVO> result = new Page<>(pageNo, pageSize, allGroups.size());
        result.setRecords(allGroups.subList(fromIndex, toIndex));
        return result;
    }

    private List<ApplicationSubmissionVO> groupSubmissionApplications(List<JobApplication> applications) {
        Map<String, List<JobApplication>> grouped = new LinkedHashMap<>();
        for (JobApplication application : applications) {
            boolean preference = StringUtils.hasText(application.getSubmissionGroupId())
                    && application.getPreferenceOrder() != null;
            String key = preference
                    ? "preference:" + application.getSubmissionGroupId().trim()
                    : "application:" + application.getId();
            grouped.computeIfAbsent(key, ignored -> new ArrayList<>()).add(application);
        }

        List<ApplicationSubmissionVO> result = new ArrayList<>();
        for (List<JobApplication> items : grouped.values()) {
            JobApplication representative = items.get(0);
            ApplicationSubmissionVO submission = new ApplicationSubmissionVO();
            BeanUtils.copyProperties(representative, submission);
            items.sort((left, right) -> Integer.compare(
                    left.getPreferenceOrder() == null ? Integer.MAX_VALUE : left.getPreferenceOrder(),
                    right.getPreferenceOrder() == null ? Integer.MAX_VALUE : right.getPreferenceOrder()
            ));
            submission.setMultiPreference(items.size() > 1);
            submission.setPreferences(new ArrayList<>(items));
            result.add(submission);
        }
        return result;
    }

    private String normalizeCompanyName(String value) {
        if (!StringUtils.hasText(value)) {
            return "未填写公司";
        }
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFKC)
                .replaceAll("[\\p{Z}\\s\\p{Cf}]+", " ")
                .strip();
        return normalized.isEmpty() ? "未填写公司" : normalized;
    }

    private QueryWrapper<JobApplication> applicationQuery(ApplicationQueryDTO query) {
        QueryWrapper<JobApplication> wrapper = new QueryWrapper<>();
        wrapper.lambda()
                .like(StringUtils.hasText(query.getCompanyName()), JobApplication::getCompanyName, query.getCompanyName())
                .eq(StringUtils.hasText(query.getCurrentStatus()), JobApplication::getCurrentStatus, query.getCurrentStatus())
                .like(StringUtils.hasText(query.getPositionType()), JobApplication::getPositionType, query.getPositionType())
                .eq(StringUtils.hasText(query.getRecruitmentType()), JobApplication::getRecruitmentType, query.getRecruitmentType())
                .like(StringUtils.hasText(query.getResumeCategory()), JobApplication::getResumeCategory, query.getResumeCategory())
                .ge(query.getAppliedStartTime() != null, JobApplication::getAppliedTime, query.getAppliedStartTime())
                .le(query.getAppliedEndTime() != null, JobApplication::getAppliedTime, query.getAppliedEndTime());
        applySort(wrapper, query);
        return wrapper;
    }

    private void applySort(QueryWrapper<JobApplication> wrapper, ApplicationQueryDTO query) {
        boolean asc = "asc".equalsIgnoreCase(query.getSortOrder());
        if ("companyName".equals(query.getSortField())) {
            wrapper.orderBy(true, asc, "company_name")
                    .orderByDesc("updated_time");
            return;
        }
        wrapper.orderBy(true, asc, EFFECTIVE_APPLIED_TIME_SQL)
                .orderByAsc("CASE WHEN preference_order IS NULL THEN 0 ELSE preference_order END")
                .orderByDesc("updated_time");
    }

    @Override
    public ApplicationDetailVO detail(Long id) {
        JobApplication application = getById(id);
        if (application == null) {
            throw new IllegalArgumentException("投递记录不存在");
        }
        List<InterviewRecord> records = interviewRecordService.listByJobId(id);
        List<InterviewNote> notes = interviewNoteService.listByJobId(id);
        List<Reminder> relatedSchedules = reminderService.lambdaQuery()
                .eq(Reminder::getRelatedApplicationId, id)
                .orderByAsc(Reminder::getRemindTime)
                .list();
        Resume resume = application.getResumeId() == null ? null : resumeService.getById(application.getResumeId());
        ApplicationDetailVO vo = new ApplicationDetailVO();
        vo.setApplication(application);
        vo.setResume(resume);
        vo.setInterviewRecords(records);
        vo.setInterviewNotes(notes);
        vo.setRelatedSchedules(relatedSchedules);
        return vo;
    }

    @Override
    public List<String> statusOptions() {
        Set<String> options = new LinkedHashSet<>(DEFAULT_STATUS_OPTIONS);
        List<JobApplication> applications = list();
        for (JobApplication application : applications) {
            addOption(options, application.getCurrentStatus());
            addProgressFlowOptions(options, application.getProgressFlow());
        }
        return new ArrayList<>(options);
    }

    @Override
    public List<String> positionTypeOptions() {
        Set<String> options = new LinkedHashSet<>(DEFAULT_POSITION_TYPE_OPTIONS);
        for (JobApplication application : list()) {
            addSplitOptions(options, application.getPositionType());
        }
        return new ArrayList<>(options);
    }

    @Override
    public List<String> resumeCategoryOptions() {
        Set<String> options = new LinkedHashSet<>(DEFAULT_RESUME_CATEGORY_OPTIONS);
        for (JobApplication application : list()) {
            addSplitOptions(options, application.getResumeCategory());
        }
        return new ArrayList<>(options);
    }

    @Override
    public JobApplication createApplication(JobApplication application) {
        prepareNewSubmission(application);
        save(application);
        return application;
    }

    @Override
    @Transactional
    public JobApplication createFromApplication(Long sourceId, JobApplication application, boolean sameSubmission) {
        JobApplication source = getById(sourceId);
        if (source == null) {
            throw new IllegalArgumentException("用于复用的原投递记录不存在");
        }
        application.setId(null);
        if (!sameSubmission) {
            prepareNewSubmission(application);
            save(application);
            return application;
        }

        String groupId = source.getSubmissionGroupId();
        if (!StringUtils.hasText(groupId)) {
            groupId = UUID.randomUUID().toString();
            source.setSubmissionGroupId(groupId);
            source.setPreferenceOrder(1);
            updateById(source);
        } else if (source.getPreferenceOrder() == null) {
            source.setPreferenceOrder(1);
            updateById(source);
        }

        Integer maxOrder = lambdaQuery()
                .eq(JobApplication::getSubmissionGroupId, groupId)
                .isNotNull(JobApplication::getPreferenceOrder)
                .list()
                .stream()
                .map(JobApplication::getPreferenceOrder)
                .max(Integer::compareTo)
                .orElse(0);
        application.setSubmissionGroupId(groupId);
        application.setPreferenceOrder(Math.max(maxOrder + 1, 2));
        if (application.getAppliedTime() == null) {
            application.setAppliedTime(source.getAppliedTime());
        }
        if (!StringUtils.hasText(application.getCurrentStatus())) {
            application.setCurrentStatus(source.getCurrentStatus());
        }
        save(application);
        return application;
    }

    private void prepareNewSubmission(JobApplication application) {
        if (!StringUtils.hasText(application.getSubmissionGroupId())) {
            application.setSubmissionGroupId(UUID.randomUUID().toString());
        }
        if (application.getPreferenceOrder() != null && application.getPreferenceOrder() < 1) {
            application.setPreferenceOrder(null);
        }
    }

    @Override
    public void updateStatus(Long id, String status) {
        JobApplication application = getById(id);
        if (application == null) {
            throw new IllegalArgumentException("投递记录不存在");
        }
        application.setCurrentStatus(status);
        updateById(application);
    }

    @Override
    public void deleteByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        removeByIds(ids);
    }

    private void addOption(Set<String> options, String value) {
        if (StringUtils.hasText(value)) {
            options.add(value.trim());
        }
    }

    private void addSplitOptions(Set<String> options, String value) {
        if (!StringUtils.hasText(value)) {
            return;
        }
        for (String item : value.split("[、,，;；]")) {
            addOption(options, item);
        }
    }

    private void addProgressFlowOptions(Set<String> options, String progressFlow) {
        if (!StringUtils.hasText(progressFlow)) {
            return;
        }
        String trimmed = progressFlow.trim();
        if (trimmed.startsWith("[")) {
            try {
                List<Map<String, Object>> steps = objectMapper.readValue(trimmed, new TypeReference<>() {
                });
                for (Map<String, Object> step : steps) {
                    Object name = step.get("name");
                    addOption(options, name == null ? null : String.valueOf(name));
                }
                return;
            } catch (Exception ignored) {
                // Fallback to the legacy line format below.
            }
        }
        for (String line : progressFlow.split("\\R")) {
            String status = line.split("\\|", 2)[0].trim();
            addOption(options, status);
        }
    }
}
