package com.jobtracker.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@TableName("application_submission")
@EqualsAndHashCode(callSuper = true)
public class ApplicationSubmission extends BaseEntity {
    @TableId(type = IdType.INPUT)
    private String id;
    private String companyName;
    private String recruitmentType;
    private String workLocation;
    private String source;
    private LocalDateTime appliedTime;
    private Long submissionOrganizationId;
    private Long employerOrganizationId;
    private Long organizationUnitId;
    private String organizationPathSnapshot;
    private String employerNameSnapshot;
    private String groupNameSnapshot;
    private String remark;
}
