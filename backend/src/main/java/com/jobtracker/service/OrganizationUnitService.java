package com.jobtracker.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.jobtracker.entity.JobApplication;
import com.jobtracker.entity.OrganizationUnit;
import com.jobtracker.vo.OrganizationUnitTreeVO;

import java.util.List;

public interface OrganizationUnitService extends IService<OrganizationUnit> {
    List<OrganizationUnitTreeVO> tree();
    OrganizationUnit createUnit(OrganizationUnit unit);
    OrganizationUnit updateUnit(Long id, OrganizationUnit unit);
    void applyOrganization(JobApplication application);
    List<Long> searchIds(String keyword);
}
