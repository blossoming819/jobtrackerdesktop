package com.jobtracker.applymate.profile.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.jobtracker.applymate.profile.dto.CandidateProfileResponse;
import com.jobtracker.applymate.profile.dto.CandidateProfileMetadataRequest;
import com.jobtracker.applymate.profile.dto.CandidateProfileSummary;
import com.jobtracker.applymate.profile.dto.CandidateProfileVersionRequest;
import com.jobtracker.applymate.profile.dto.ProfileSnapshotResponse;
import com.jobtracker.applymate.profile.dto.ProfileDiffResponse;
import com.jobtracker.applymate.profile.service.CandidateProfileService;
import com.jobtracker.applymate.profile.service.ProfileDiffService;
import com.jobtracker.common.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/applymate/v1/profile")
public class CandidateProfileController {
    private final CandidateProfileService candidateProfileService;
    private final ProfileDiffService profileDiffService;

    @GetMapping
    public Result<CandidateProfileResponse> current() {
        return Result.ok(candidateProfileService.current());
    }

    @PutMapping
    public Result<CandidateProfileResponse> save(@RequestBody JsonNode content) {
        return Result.ok(candidateProfileService.save(content));
    }

    @GetMapping("/versions")
    public Result<List<CandidateProfileSummary>> versions() {
        return Result.ok(candidateProfileService.versions());
    }

    @PostMapping("/versions")
    public Result<CandidateProfileResponse> createVersion(@RequestBody CandidateProfileVersionRequest request) {
        return Result.ok(candidateProfileService.create(request));
    }

    @GetMapping("/versions/{profileId}")
    public Result<CandidateProfileResponse> version(@PathVariable String profileId) {
        return Result.ok(candidateProfileService.get(profileId));
    }

    @PutMapping("/versions/{profileId}")
    public Result<CandidateProfileResponse> saveVersion(@PathVariable String profileId, @RequestBody JsonNode content) {
        return Result.ok(candidateProfileService.save(profileId, content));
    }

    @PatchMapping("/versions/{profileId}")
    public Result<CandidateProfileResponse> updateVersion(@PathVariable String profileId, @RequestBody CandidateProfileMetadataRequest request) {
        return Result.ok(candidateProfileService.updateMetadata(profileId, request));
    }

    @DeleteMapping("/versions/{profileId}")
    public Result<Void> deleteVersion(@PathVariable String profileId) {
        candidateProfileService.delete(profileId);
        return Result.ok();
    }

    @GetMapping("/versions/{profileId}/snapshots")
    public Result<List<ProfileSnapshotResponse>> versionSnapshots(@PathVariable String profileId, @org.springframework.web.bind.annotation.RequestParam(defaultValue = "5") int limit) {
        return Result.ok(candidateProfileService.snapshots(profileId, limit));
    }

    @PutMapping("/versions/{profileId}/snapshots/{snapshotId}/restore")
    public Result<CandidateProfileResponse> restoreVersion(@PathVariable String profileId, @PathVariable Long snapshotId) {
        return Result.ok(candidateProfileService.restore(profileId, snapshotId));
    }

    @GetMapping("/snapshots")
    public Result<List<ProfileSnapshotResponse>> snapshots(@org.springframework.web.bind.annotation.RequestParam(defaultValue = "5") int limit) {
        return Result.ok(candidateProfileService.snapshots(limit));
    }

    @PutMapping("/diff")
    public Result<ProfileDiffResponse> diff(@RequestBody JsonNode draft) {
        return Result.ok(profileDiffService.compare(candidateProfileService.current().content(), draft));
    }

    @PutMapping("/snapshots/{snapshotId}/restore")
    public Result<CandidateProfileResponse> restore(@PathVariable Long snapshotId) {
        return Result.ok(candidateProfileService.restore(snapshotId));
    }
}
