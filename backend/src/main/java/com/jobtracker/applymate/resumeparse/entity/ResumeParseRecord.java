package com.jobtracker.applymate.resumeparse.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("resume_parse_record")
public class ResumeParseRecord {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long resumeId;
    private String contentHash;
    private String extractor;
    private String status;
    private Integer extractedLength;
    private String resultJson;
    private String errorCode;
    private LocalDateTime createdTime;
}
