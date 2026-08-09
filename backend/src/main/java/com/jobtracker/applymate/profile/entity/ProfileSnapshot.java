package com.jobtracker.applymate.profile.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("profile_snapshot")
public class ProfileSnapshot {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String profileId;
    private Integer revision;
    private String contentJson;
    private LocalDateTime createdTime;
}
