package com.jobtracker.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jobtracker.entity.JobApplication;
import com.jobtracker.entity.OrganizationUnit;
import com.jobtracker.mapper.OrganizationUnitMapper;
import com.jobtracker.service.OrganizationUnitService;
import com.jobtracker.vo.OrganizationUnitTreeVO;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class OrganizationUnitServiceImpl extends ServiceImpl<OrganizationUnitMapper, OrganizationUnit> implements OrganizationUnitService {
    private static final String PATH_SEPARATOR = " / ";

    @Override
    public List<OrganizationUnitTreeVO> tree() {
        List<OrganizationUnit> units = lambdaQuery()
                .eq(OrganizationUnit::getActive, true)
                .orderByAsc(OrganizationUnit::getSortOrder)
                .orderByAsc(OrganizationUnit::getName)
                .list();
        Map<Long, OrganizationUnitTreeVO> nodes = new LinkedHashMap<>();
        for (OrganizationUnit unit : units) {
            OrganizationUnitTreeVO node = new OrganizationUnitTreeVO();
            BeanUtils.copyProperties(unit, node);
            nodes.put(unit.getId(), node);
        }
        List<OrganizationUnitTreeVO> roots = new ArrayList<>();
        for (OrganizationUnitTreeVO node : nodes.values()) {
            OrganizationUnitTreeVO parent = node.getParentId() == null ? null : nodes.get(node.getParentId());
            if (parent == null) roots.add(node);
            else parent.getChildren().add(node);
        }
        roots.forEach(root -> populatePaths(root, ""));
        return roots;
    }

    @Override
    @Transactional
    public OrganizationUnit createUnit(OrganizationUnit unit) {
        String name = normalizeName(unit.getName());
        if (!StringUtils.hasText(name)) throw new IllegalArgumentException("请填写组织名称");
        if (unit.getParentId() != null) {
            OrganizationUnit parent = getById(unit.getParentId());
            if (parent == null || !Boolean.TRUE.equals(parent.getActive())) {
                throw new IllegalArgumentException("上级组织不存在或已停用");
            }
        }
        OrganizationUnit existing = lambdaQuery()
                .eq(unit.getParentId() != null, OrganizationUnit::getParentId, unit.getParentId())
                .isNull(unit.getParentId() == null, OrganizationUnit::getParentId)
                .eq(OrganizationUnit::getName, name)
                .one();
        if (existing != null) return existing;
        unit.setName(name);
        unit.setUnitType(StringUtils.hasText(unit.getUnitType()) ? unit.getUnitType().trim() : "OTHER");
        unit.setCompanyEntity(Boolean.TRUE.equals(unit.getCompanyEntity()));
        unit.setSortOrder(unit.getSortOrder() == null ? 0 : unit.getSortOrder());
        unit.setActive(unit.getActive() == null || unit.getActive());
        save(unit);
        return unit;
    }

    @Override
    @Transactional
    public void applyOrganization(JobApplication application) {
        if (application.getOrganizationUnitId() == null) {
            if (!StringUtils.hasText(application.getCompanyName())) return;
            OrganizationUnit root = ensureRootCompany(application.getCompanyName());
            application.setOrganizationUnitId(root.getId());
        }
        List<OrganizationUnit> path = resolvePath(application.getOrganizationUnitId());
        if (path.isEmpty() || path.stream().anyMatch(item -> !Boolean.TRUE.equals(item.getActive()))) {
            throw new IllegalArgumentException("选择的组织不存在或已停用");
        }
        OrganizationUnit root = path.get(0);
        OrganizationUnit employer = path.stream()
                .filter(item -> Boolean.TRUE.equals(item.getCompanyEntity()))
                .reduce((left, right) -> right)
                .orElse(root);
        application.setSubmissionOrganizationId(root.getId());
        application.setEmployerOrganizationId(employer.getId());
        application.setOrganizationPathSnapshot(path.stream().map(OrganizationUnit::getName).reduce((a, b) -> a + PATH_SEPARATOR + b).orElse(""));
        application.setEmployerNameSnapshot(employer.getName());
        application.setGroupNameSnapshot(root.getName());
        application.setCompanyName(employer.getName());
    }

    @Override
    public List<Long> searchIds(String keyword) {
        if (!StringUtils.hasText(keyword)) return List.of();
        return lambdaQuery()
                .and(value -> value.like(OrganizationUnit::getName, keyword.trim())
                        .or().like(OrganizationUnit::getAliases, keyword.trim()))
                .list()
                .stream()
                .map(OrganizationUnit::getId)
                .toList();
    }

    private OrganizationUnit ensureRootCompany(String companyName) {
        String normalized = normalizeName(companyName);
        List<OrganizationUnit> roots = lambdaQuery().isNull(OrganizationUnit::getParentId).list();
        for (OrganizationUnit unit : roots) {
            if (normalizeKey(unit.getName()).equals(normalizeKey(normalized))) return unit;
        }
        OrganizationUnit unit = new OrganizationUnit();
        unit.setName(normalized);
        unit.setUnitType("COMPANY");
        unit.setCompanyEntity(true);
        unit.setActive(true);
        unit.setSortOrder(0);
        save(unit);
        return unit;
    }

    private List<OrganizationUnit> resolvePath(Long leafId) {
        Map<Long, OrganizationUnit> all = new HashMap<>();
        for (OrganizationUnit item : list()) all.put(item.getId(), item);
        List<OrganizationUnit> reversed = new ArrayList<>();
        OrganizationUnit cursor = all.get(leafId);
        int guard = all.size() + 1;
        while (cursor != null && guard-- > 0) {
            reversed.add(cursor);
            cursor = cursor.getParentId() == null ? null : all.get(cursor.getParentId());
        }
        if (cursor != null) throw new IllegalArgumentException("组织层级存在循环关系");
        java.util.Collections.reverse(reversed);
        return reversed;
    }

    private void populatePaths(OrganizationUnitTreeVO node, String parentPath) {
        String path = StringUtils.hasText(parentPath) ? parentPath + PATH_SEPARATOR + node.getName() : node.getName();
        node.setFullPath(path);
        node.getChildren().forEach(child -> populatePaths(child, path));
    }

    private String normalizeName(String value) {
        if (!StringUtils.hasText(value)) return "";
        return Normalizer.normalize(value, Normalizer.Form.NFKC).replaceAll("[\\p{Z}\\s\\p{Cf}]+", " ").strip();
    }

    private String normalizeKey(String value) {
        return normalizeName(value).toLowerCase(Locale.ROOT);
    }
}
