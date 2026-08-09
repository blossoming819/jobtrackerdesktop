package com.jobtracker.applymate.profile.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.jobtracker.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@TableName("candidate_profile")
@EqualsAndHashCode(callSuper = true)
public class CandidateProfile extends BaseEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String profileId;
    private String schemaVersion;
    private String contentJson;
    private Integer revision;
}
