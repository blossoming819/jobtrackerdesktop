package com.jobtracker.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ApplicationPeriodStatsVO {
    private Long currentCount;
    private Long previousCount;
    private Long change;
}
