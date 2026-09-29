package com.jobtracker.vo;

import com.jobtracker.entity.JobApplication;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ApplicationCompanyGroupVO {
    private String companyName;
    private List<JobApplication> children = new ArrayList<>();
}
