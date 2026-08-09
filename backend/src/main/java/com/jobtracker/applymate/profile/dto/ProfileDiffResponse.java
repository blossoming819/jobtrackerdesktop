package com.jobtracker.applymate.profile.dto;

import java.util.List;

public record ProfileDiffResponse(List<Change> additions, List<Change> updates, List<Change> conflicts) {
    public record Change(String path, Object currentValue, Object draftValue) { }
}
