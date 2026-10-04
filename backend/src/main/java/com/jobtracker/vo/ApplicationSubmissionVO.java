package com.jobtracker.vo;

import com.jobtracker.entity.JobApplication;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.ArrayList;
import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
public class ApplicationSubmissionVO extends JobApplication {
    private boolean multiPreference;
    private List<JobApplication> preferences = new ArrayList<>();
}
