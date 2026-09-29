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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class JobApplicationServiceImpl extends ServiceImpl<JobApplicationMapper, JobApplication> implements JobApplicationService {
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
        long pageNo = Math.max(query.getPageNo(), 1L);
        long pageSize = Math.max(query.getPageSize(), 1L);
        int fromIndex = (int) Math.min((pageNo - 1) * pageSize, allGroups.size());
        int toIndex = (int) Math.min(fromIndex + pageSize, allGroups.size());
        Page<ApplicationCompanyGroupVO> result = new Page<>(pageNo, pageSize, allGroups.size());
        result.setRecords(allGroups.subList(fromIndex, toIndex));
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
        wrapper.orderByAsc("applied_time IS NULL")
                .orderBy(true, asc, "applied_time")
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
