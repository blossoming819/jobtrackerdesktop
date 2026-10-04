package com.jobtracker.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jobtracker.common.Result;
import com.jobtracker.dto.ApplicationQueryDTO;
import com.jobtracker.dto.BatchApplicationIdsDTO;
import com.jobtracker.dto.StatusUpdateDTO;
import com.jobtracker.entity.JobApplication;
import com.jobtracker.entity.Resume;
import com.jobtracker.service.JobApplicationService;
import com.jobtracker.service.ResumeService;
import com.jobtracker.vo.ApplicationDetailVO;
import com.jobtracker.vo.ApplicationCompanyGroupVO;
import com.jobtracker.vo.ApplicationSubmissionVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/applications")
public class JobApplicationController {
    private final JobApplicationService jobApplicationService;
    private final ResumeService resumeService;

    @GetMapping
    public Result<Page<JobApplication>> page(ApplicationQueryDTO query) {
        return Result.ok(jobApplicationService.pageApplications(query));
    }

    @GetMapping("/grouped-by-submission")
    public Result<Page<ApplicationSubmissionVO>> pageGroupedBySubmission(ApplicationQueryDTO query) {
        return Result.ok(jobApplicationService.pageApplicationSubmissions(query));
    }

    @GetMapping("/grouped-by-company")
    public Result<Page<ApplicationCompanyGroupVO>> pageGroupedByCompany(ApplicationQueryDTO query) {
        return Result.ok(jobApplicationService.pageApplicationCompanies(query));
    }

    @GetMapping("/status-options")
    public Result<List<String>> statusOptions() {
        return Result.ok(jobApplicationService.statusOptions());
    }

    @GetMapping("/position-type-options")
    public Result<List<String>> positionTypeOptions() {
        return Result.ok(jobApplicationService.positionTypeOptions());
    }

    @GetMapping("/resume-category-options")
    public Result<List<String>> resumeCategoryOptions() {
        return Result.ok(jobApplicationService.resumeCategoryOptions());
    }

    @GetMapping("/{id}")
    public Result<ApplicationDetailVO> detail(@PathVariable Long id) {
        return Result.ok(jobApplicationService.detail(id));
    }

    @GetMapping("/{id}/resume/download")
    public ResponseEntity<Resource> downloadResumeForApplication(@PathVariable Long id) {
        JobApplication application = jobApplicationService.getById(id);
        if (application == null || application.getResumeId() == null) {
            throw new IllegalArgumentException("当前投递未绑定简历");
        }
        Resume resume = resumeService.getById(application.getResumeId());
        if (resume == null) {
            throw new IllegalArgumentException("绑定的简历不存在");
        }
        String fileName = resolveDownloadName(application.getResumeAlias(), resume.getFileName());
        String encodedFileName = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encodedFileName)
                .body(resumeService.download(resume.getId()));
    }

    @PostMapping
    public Result<JobApplication> create(@RequestBody JobApplication application) {
        return Result.ok(jobApplicationService.createApplication(application));
    }

    @PostMapping("/{sourceId}/create-from")
    public Result<JobApplication> createFrom(
            @PathVariable Long sourceId,
            @RequestParam(defaultValue = "false") boolean sameSubmission,
            @RequestBody JobApplication application
    ) {
        return Result.ok(jobApplicationService.createFromApplication(sourceId, application, sameSubmission));
    }

    @PutMapping("/{id}")
    public Result<JobApplication> update(@PathVariable Long id, @RequestBody JobApplication application) {
        application.setId(id);
        jobApplicationService.updateById(application);
        return Result.ok(application);
    }

    @PatchMapping("/{id}/status")
    public Result<Void> updateStatus(@PathVariable Long id, @Valid @RequestBody StatusUpdateDTO dto) {
        jobApplicationService.updateStatus(id, dto.getCurrentStatus());
        return Result.ok();
    }

    @DeleteMapping("/batch")
    public Result<Void> batchDelete(@Valid @RequestBody BatchApplicationIdsDTO dto) {
        jobApplicationService.deleteByIds(dto.getIds());
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        jobApplicationService.removeById(id);
        return Result.ok();
    }

    private String resolveDownloadName(String alias, String originalName) {
        String requestedName = StringUtils.hasText(alias) ? alias.trim() : originalName;
        int originalExtensionIndex = originalName.lastIndexOf('.');
        if (originalExtensionIndex < 0) {
            return requestedName;
        }
        String extension = originalName.substring(originalExtensionIndex);
        int requestedExtensionIndex = requestedName.lastIndexOf('.');
        if (requestedExtensionIndex < 0) {
            return requestedName + extension;
        }
        return requestedName.toLowerCase().endsWith(extension.toLowerCase())
                ? requestedName
                : requestedName.substring(0, requestedExtensionIndex) + extension;
    }
}
