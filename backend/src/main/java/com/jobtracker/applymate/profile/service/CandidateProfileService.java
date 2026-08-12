package com.jobtracker.applymate.profile.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.jobtracker.applymate.profile.dto.CandidateProfileResponse;
import com.jobtracker.applymate.profile.dto.CandidateProfileMetadataRequest;
import com.jobtracker.applymate.profile.dto.CandidateProfileSummary;
import com.jobtracker.applymate.profile.dto.CandidateProfileVersionRequest;
import com.jobtracker.applymate.profile.dto.ProfileSnapshotResponse;

import java.util.List;

public interface CandidateProfileService {
    CandidateProfileResponse current();

    CandidateProfileResponse get(String profileId);

    List<CandidateProfileSummary> versions();

    CandidateProfileResponse create(CandidateProfileVersionRequest request);

    CandidateProfileResponse updateMetadata(String profileId, CandidateProfileMetadataRequest request);

    void delete(String profileId);

    CandidateProfileResponse save(JsonNode content);

    CandidateProfileResponse save(String profileId, JsonNode content);

    List<ProfileSnapshotResponse> snapshots(int limit);

    List<ProfileSnapshotResponse> snapshots(String profileId, int limit);

    CandidateProfileResponse restore(Long snapshotId);

    CandidateProfileResponse restore(String profileId, Long snapshotId);
}
