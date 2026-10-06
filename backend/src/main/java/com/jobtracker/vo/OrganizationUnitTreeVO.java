package com.jobtracker.vo;

import com.jobtracker.entity.OrganizationUnit;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.ArrayList;
import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
public class OrganizationUnitTreeVO extends OrganizationUnit {
    private String fullPath;
    private List<OrganizationUnitTreeVO> children = new ArrayList<>();
}
