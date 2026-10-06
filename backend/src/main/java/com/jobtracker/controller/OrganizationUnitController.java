package com.jobtracker.controller;

import com.jobtracker.common.Result;
import com.jobtracker.entity.OrganizationUnit;
import com.jobtracker.service.OrganizationUnitService;
import com.jobtracker.vo.OrganizationUnitTreeVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/organization-units")
public class OrganizationUnitController {
    private final OrganizationUnitService organizationUnitService;

    @GetMapping("/tree")
    public Result<List<OrganizationUnitTreeVO>> tree() {
        return Result.ok(organizationUnitService.tree());
    }

    @PostMapping
    public Result<OrganizationUnit> create(@RequestBody OrganizationUnit unit) {
        return Result.ok(organizationUnitService.createUnit(unit));
    }
}
