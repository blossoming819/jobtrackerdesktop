package com.jobtracker.applymate.security;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data @TableName("applymate_extension_pairing")
public class ExtensionPairing {
  @TableId(type = IdType.AUTO) private Long id;
  private String extensionId; private String displayName; private String tokenHash; private String status;
  private LocalDateTime createdTime; private LocalDateTime approvedTime;
}
