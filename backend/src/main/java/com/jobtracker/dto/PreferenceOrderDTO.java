package com.jobtracker.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class PreferenceOrderDTO {
    @NotEmpty(message = "请提供完整的志愿顺序")
    private List<Long> ids;
}
