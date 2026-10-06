package com.jobtracker.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.jobtracker.entity.JobApplication;
import com.jobtracker.entity.ApplicationSubmission;
import com.jobtracker.entity.OrganizationUnit;
import com.jobtracker.vo.OrganizationUnitTreeVO;

import java.util.List;

public interface OrganizationUnitService extends IService<OrganizationUnit> {
    List<OrganizationUnitTreeVO> tree();
    OrganizationUnit createUnit(OrganizationUnit unit);
    OrganizationUnit updateUnit(Long id, OrganizationUnit unit);
    void deleteUnit(Long id);
    void applyOrganization(JobApplication application);
    void applySubmissionOrganization(ApplicationSubmission submission);
    List<Long> searchIds(String keyword);
}
