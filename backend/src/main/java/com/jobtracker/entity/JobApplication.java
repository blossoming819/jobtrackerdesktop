package com.jobtracker.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@TableName("job_application")
@EqualsAndHashCode(callSuper = true)
public class JobApplication extends BaseEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String companyName;
    private String positionName;
    private String positionType;
    private String recruitmentType;
    private String resumeCategory;
    private String resumeAlias;
    private String workLocation;
    private String salary;
    private String source;
    private String jobLink;
    private String jobDescription;
    private String currentStatus;
    private String progressFlow;
    private String progressResult;
    private LocalDateTime progressOperatedTime;
    private LocalDateTime appliedTime;
    private Long resumeId;
    private String profileId;
    private String remark;
}
