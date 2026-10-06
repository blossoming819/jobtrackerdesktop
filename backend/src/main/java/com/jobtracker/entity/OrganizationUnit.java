package com.jobtracker.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@TableName("organization_unit")
@EqualsAndHashCode(callSuper = true)
public class OrganizationUnit extends BaseEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long parentId;
    private String name;
    private String unitType;
    private Boolean companyEntity;
    private String aliases;
    private Integer sortOrder;
    private Boolean active;
}
