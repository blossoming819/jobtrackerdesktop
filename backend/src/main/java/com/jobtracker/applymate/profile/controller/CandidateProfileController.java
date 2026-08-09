package com.jobtracker.applymate.profile.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.jobtracker.applymate.profile.dto.CandidateProfileResponse;
import com.jobtracker.applymate.profile.dto.ProfileSnapshotResponse;
import com.jobtracker.applymate.profile.dto.ProfileDiffResponse;
import com.jobtracker.applymate.profile.service.CandidateProfileService;
import com.jobtracker.applymate.profile.service.ProfileDiffService;
import com.jobtracker.common.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
