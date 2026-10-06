package com.jobtracker.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jobtracker.entity.ApplicationSubmission;
import com.jobtracker.entity.JobApplication;
import com.jobtracker.entity.OrganizationUnit;
import com.jobtracker.mapper.ApplicationSubmissionMapper;
import com.jobtracker.mapper.JobApplicationMapper;
import com.jobtracker.mapper.OrganizationUnitMapper;
import com.jobtracker.service.OrganizationUnitService;
import com.jobtracker.vo.OrganizationUnitTreeVO;
import lombok.RequiredArgsConstructor;
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
@RequiredArgsConstructor
public class OrganizationUnitServiceImpl extends ServiceImpl<OrganizationUnitMapper, OrganizationUnit> implements OrganizationUnitService {
    private static final String PATH_SEPARATOR = " / ";
    private final JobApplicationMapper jobApplicationMapper;
    private final ApplicationSubmissionMapper applicationSubmissionMapper;

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
    public OrganizationUnit updateUnit(Long id, OrganizationUnit unit) {
        OrganizationUnit existing = getById(id);
        if (existing == null) throw new IllegalArgumentException("组织节点不存在");
        String name = normalizeName(unit.getName());
        if (!StringUtils.hasText(name)) throw new IllegalArgumentException("请填写组织名称");
        Long parentId = unit.getParentId();
        if (id.equals(parentId)) throw new IllegalArgumentException("组织节点不能把自己设为上级");
        if (parentId != null) {
            OrganizationUnit parent = getById(parentId);
            if (parent == null || !Boolean.TRUE.equals(parent.getActive())) {
                throw new IllegalArgumentException("上级组织不存在或已停用");
            }
            assertNotDescendant(id, parentId);
        }
        OrganizationUnit duplicate = lambdaQuery()
                .eq(parentId != null, OrganizationUnit::getParentId, parentId)
                .isNull(parentId == null, OrganizationUnit::getParentId)
                .eq(OrganizationUnit::getName, name)
                .ne(OrganizationUnit::getId, id)
                .one();
        if (duplicate != null) throw new IllegalArgumentException("同一上级下已存在同名组织节点");
        existing.setParentId(parentId);
        existing.setName(name);
        existing.setUnitType(StringUtils.hasText(unit.getUnitType()) ? unit.getUnitType().trim() : existing.getUnitType());
        existing.setCompanyEntity(unit.getCompanyEntity() == null ? existing.getCompanyEntity() : unit.getCompanyEntity());
        existing.setAliases(unit.getAliases());
        existing.setSortOrder(unit.getSortOrder() == null ? existing.getSortOrder() : unit.getSortOrder());
        existing.setActive(unit.getActive() == null ? existing.getActive() : unit.getActive());
        updateById(existing);
        return existing;
    }

    @Override
    @Transactional
    public void deleteUnit(Long id) {
        OrganizationUnit existing = getById(id);
        if (existing == null) throw new IllegalArgumentException("组织节点不存在");
        long childCount = lambdaQuery().eq(OrganizationUnit::getParentId, id).count();
        if (childCount > 0) {
            throw new IllegalArgumentException("该组织节点仍有 " + childCount + " 个下级节点，请先从最末级节点开始删除或调整层级");
        }
        long applicationCount = jobApplicationMapper.selectCount(new LambdaQueryWrapper<JobApplication>()
                .and(wrapper -> wrapper.eq(JobApplication::getOrganizationUnitId, id)
                        .or().eq(JobApplication::getSubmissionOrganizationId, id)
                        .or().eq(JobApplication::getEmployerOrganizationId, id)));
        if (applicationCount > 0) {
            throw new IllegalArgumentException("该组织节点仍关联 " + applicationCount + " 条投递记录，请先删除或改绑相关岗位记录");
        }

        List<ApplicationSubmission> submissions = applicationSubmissionMapper.selectList(new LambdaQueryWrapper<ApplicationSubmission>()
                .and(wrapper -> wrapper.eq(ApplicationSubmission::getOrganizationUnitId, id)
                        .or().eq(ApplicationSubmission::getSubmissionOrganizationId, id)
                        .or().eq(ApplicationSubmission::getEmployerOrganizationId, id)));
        for (ApplicationSubmission submission : submissions) {
            long preferenceCount = jobApplicationMapper.selectCount(new LambdaQueryWrapper<JobApplication>()
                    .eq(JobApplication::getSubmissionGroupId, submission.getId()));
            if (preferenceCount > 0) {
                throw new IllegalArgumentException("该组织节点仍关联网申父记录，请先删除或改绑该记录下的岗位志愿");
            }
            // 清理历史上可能遗留的空父记录，不会影响任何岗位投递。
            applicationSubmissionMapper.deleteById(submission.getId());
        }
        removeById(id);
    }

    private void assertNotDescendant(Long id, Long proposedParentId) {
        OrganizationUnit cursor = getById(proposedParentId);
        int guard = (int) count() + 1;
        while (cursor != null && guard-- > 0) {
            if (id.equals(cursor.getId())) throw new IllegalArgumentException("不能把组织节点移动到自己的下级");
            cursor = cursor.getParentId() == null ? null : getById(cursor.getParentId());
        }
        if (cursor != null) throw new IllegalArgumentException("组织层级存在循环关系");
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
