package com.jobtracker.applymate.profile.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.jobtracker.applymate.profile.dto.CandidateProfileResponse;
import com.jobtracker.applymate.profile.dto.ProfileSnapshotResponse;

import java.util.List;

public interface CandidateProfileService {
    CandidateProfileResponse current();

    CandidateProfileResponse save(JsonNode content);

    List<ProfileSnapshotResponse> snapshots(int limit);

    CandidateProfileResponse restore(Long snapshotId);
}
