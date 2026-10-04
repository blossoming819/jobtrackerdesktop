package com.jobtracker.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.jobtracker.dto.ApplicationQueryDTO;
import com.jobtracker.entity.JobApplication;
import com.jobtracker.vo.ApplicationDetailVO;
import com.jobtracker.vo.ApplicationCompanyGroupVO;

import java.util.List;

public interface JobApplicationService extends IService<JobApplication> {
    Page<JobApplication> pageApplications(ApplicationQueryDTO query);
    Page<ApplicationCompanyGroupVO> pageApplicationCompanies(ApplicationQueryDTO query);
    ApplicationDetailVO detail(Long id);
    List<String> statusOptions();
    List<String> positionTypeOptions();
    List<String> resumeCategoryOptions();
    JobApplication createApplication(JobApplication application);
    JobApplication createFromApplication(Long sourceId, JobApplication application, boolean sameSubmission);
    void updateStatus(Long id, String status);
    void deleteByIds(List<Long> ids);
}
