package com.jobtracker.applymate.profile.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jobtracker.applymate.profile.dto.CandidateProfileMetadataRequest;
import com.jobtracker.applymate.profile.dto.CandidateProfileResponse;
import com.jobtracker.applymate.profile.dto.CandidateProfileSummary;
import com.jobtracker.applymate.profile.dto.CandidateProfileVersionRequest;
import com.jobtracker.applymate.profile.dto.ProfileSnapshotResponse;
import com.jobtracker.applymate.profile.entity.CandidateProfile;
import com.jobtracker.applymate.profile.entity.ProfileSnapshot;
import com.jobtracker.applymate.profile.mapper.CandidateProfileMapper;
import com.jobtracker.applymate.profile.mapper.ProfileSnapshotMapper;
import com.jobtracker.applymate.profile.service.CandidateProfileService;
import com.jobtracker.entity.JobApplication;
import com.jobtracker.mapper.JobApplicationMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CandidateProfileServiceImpl implements CandidateProfileService {
    public static final String DEFAULT_PROFILE_ID = "default";
    private static final String SCHEMA_VERSION = "0.1";

    private final CandidateProfileMapper candidateProfileMapper;
    private final ProfileSnapshotMapper profileSnapshotMapper;
    private final JobApplicationMapper jobApplicationMapper;
    private final ObjectMapper objectMapper;

    @Override
    public CandidateProfileResponse current() {
        return get(DEFAULT_PROFILE_ID);
    }

    @Override
    public CandidateProfileResponse get(String profileId) {
        String resolvedId = resolveProfileId(profileId);
        CandidateProfile profile = find(resolvedId);
        return profile == null ? emptyResponse(resolvedId) : toResponse(profile);
    }

    @Override
    public List<CandidateProfileSummary> versions() {
        return candidateProfileMapper.selectList(new LambdaQueryWrapper<CandidateProfile>().orderByAsc(CandidateProfile::getId))
                .stream()
                .map(this::toSummary)
                .sorted(Comparator.comparing(CandidateProfileSummary::defaultProfile).reversed()
                        .thenComparing(CandidateProfileSummary::updatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    @Override
    @Transactional
    public CandidateProfileResponse create(CandidateProfileVersionRequest request) {
        if (request == null || !StringUtils.hasText(request.name())) {
            throw new IllegalArgumentException("档案版本名称不能为空");
        }
        String profileId = "profile_" + UUID.randomUUID().toString().replace("-", "");
        CandidateProfile source = request.content() == null ? find(resolveProfileId(request.copyFromProfileId())) : null;
        JsonNode sourceContent = request.content() != null ? request.content() : source == null ? emptyContent(profileId) : parse(source.getContentJson()).deepCopy();
        JsonNode normalized = normalizeAndValidate(sourceContent, profileId);

        CandidateProfile profile = new CandidateProfile();
        profile.setProfileId(profileId);
        profile.setProfileName(request.name().trim());
        profile.setProfileDescription(trimToNull(request.description()));
        profile.setSourceResumeId(request.sourceResumeId());
        profile.setSchemaVersion(SCHEMA_VERSION);
        profile.setContentJson(serialize(normalized));
        profile.setRevision(1);
        candidateProfileMapper.insert(profile);
        appendSnapshot(profileId, 1, profile.getContentJson(), profile.getProfileDescription());
        return toResponse(profile);
    }

    @Override
    public CandidateProfileResponse updateMetadata(String profileId, CandidateProfileMetadataRequest request) {
        CandidateProfile profile = requireProfile(profileId);
        if (request == null || !StringUtils.hasText(request.name())) {
            throw new IllegalArgumentException("档案版本名称不能为空");
        }
        candidateProfileMapper.update(null, new LambdaUpdateWrapper<CandidateProfile>()
                .eq(CandidateProfile::getId, profile.getId())
                .set(CandidateProfile::getProfileName, request.name().trim())
                .set(CandidateProfile::getProfileDescription, trimToNull(request.description()))
                .set(CandidateProfile::getSourceResumeId, request.sourceResumeId()));
        return toResponse(requireProfile(profileId));
    }

    @Override
    @Transactional
    public void delete(String profileId) {
        String resolvedId = resolveProfileId(profileId);
        if (DEFAULT_PROFILE_ID.equals(resolvedId)) {
            throw new IllegalArgumentException("默认档案不能删除");
        }
        CandidateProfile profile = requireProfile(resolvedId);
        jobApplicationMapper.update(null, new LambdaUpdateWrapper<JobApplication>()
                .eq(JobApplication::getProfileId, resolvedId)
                .set(JobApplication::getProfileId, null));
        profileSnapshotMapper.delete(new LambdaQueryWrapper<ProfileSnapshot>()
                .eq(ProfileSnapshot::getProfileId, resolvedId));
        candidateProfileMapper.deleteById(profile.getId());
    }

    @Override
    public CandidateProfileResponse save(JsonNode content) {
        return save(DEFAULT_PROFILE_ID, content);
    }

    @Override
    @Transactional
    public CandidateProfileResponse save(String profileId, JsonNode content) {
        String resolvedId = resolveProfileId(profileId);
        JsonNode normalized = normalizeAndValidate(content, resolvedId);
        CandidateProfile existing = find(resolvedId);
        if (existing == null && !DEFAULT_PROFILE_ID.equals(resolvedId)) {
            throw new IllegalArgumentException("个人档案版本不存在");
        }
        if (existing != null && normalized.equals(parse(existing.getContentJson()))) {
            return toResponse(existing);
        }
        int revision = existing == null ? 1 : existing.getRevision() + 1;
        String serialized = serialize(normalized);

        CandidateProfile profile = existing == null ? new CandidateProfile() : existing;
        profile.setProfileId(resolvedId);
        if (!StringUtils.hasText(profile.getProfileName())) profile.setProfileName(defaultName(resolvedId));
        profile.setSchemaVersion(SCHEMA_VERSION);
        profile.setContentJson(serialized);
        profile.setRevision(revision);
        if (existing == null) candidateProfileMapper.insert(profile); else candidateProfileMapper.updateById(profile);
        appendSnapshot(resolvedId, revision, serialized, profile.getProfileDescription());
        return toResponse(profile);
    }

    @Override
    public List<ProfileSnapshotResponse> snapshots(int limit) {
        return snapshots(DEFAULT_PROFILE_ID, limit);
    }

    @Override
    public List<ProfileSnapshotResponse> snapshots(String profileId, int limit) {
        return profileSnapshotMapper.selectList(new LambdaQueryWrapper<ProfileSnapshot>()
                        .eq(ProfileSnapshot::getProfileId, resolveProfileId(profileId))
                        .orderByDesc(ProfileSnapshot::getRevision))
                .stream()
                .limit(Math.max(1, Math.min(limit, 50)))
                .map(snapshot -> new ProfileSnapshotResponse(snapshot.getId(), snapshot.getRevision(), snapshot.getProfileDescription(), snapshot.getCreatedTime()))
                .toList();
    }

    @Override
    public CandidateProfileResponse restore(Long snapshotId) {
        ProfileSnapshot snapshot = profileSnapshotMapper.selectById(snapshotId);
        if (snapshot == null) throw new IllegalArgumentException("档案快照不存在");
        return restore(snapshot.getProfileId(), snapshotId);
    }

    @Override
    public CandidateProfileResponse restore(String profileId, Long snapshotId) {
        ProfileSnapshot snapshot = profileSnapshotMapper.selectById(snapshotId);
        String resolvedId = resolveProfileId(profileId);
        if (snapshot == null || !resolvedId.equals(snapshot.getProfileId())) throw new IllegalArgumentException("档案快照不存在");
        return save(resolvedId, parse(snapshot.getContentJson()));
    }

    private CandidateProfile find(String profileId) {
        return candidateProfileMapper.selectOne(new LambdaQueryWrapper<CandidateProfile>().eq(CandidateProfile::getProfileId, profileId));
    }

    private CandidateProfile requireProfile(String profileId) {
        CandidateProfile profile = find(resolveProfileId(profileId));
        if (profile == null) throw new IllegalArgumentException("个人档案版本不存在");
        return profile;
    }

    private JsonNode normalizeAndValidate(JsonNode content, String profileId) {
        if (content == null || !content.isObject()) throw new IllegalArgumentException("个人档案必须是 JSON 对象");
        ObjectNode profile = ((ObjectNode) content).deepCopy();
        profile.put("schemaVersion", SCHEMA_VERSION);
        profile.put("profileId", profileId);
        requireObject(profile, "basic");
        requireObject(profile, "contact");
        requireObject(profile, "fieldMeta");
        ArrayNode education = requireArray(profile, "education");
        for (JsonNode entry : education) {
            if (!entry.isObject() || entry.path("id").asText().isBlank() || entry.path("school").asText().isBlank()) {
                throw new IllegalArgumentException("每条教育经历必须包含 id 和 school");
            }
        }
        return profile;
    }

    private ObjectNode requireObject(ObjectNode parent, String field) {
        JsonNode node = parent.get(field);
        if (node == null) { ObjectNode created = objectMapper.createObjectNode(); parent.set(field, created); return created; }
        if (!node.isObject()) throw new IllegalArgumentException(field + " 必须是对象");
        return (ObjectNode) node;
    }

    private ArrayNode requireArray(ObjectNode parent, String field) {
        JsonNode node = parent.get(field);
        if (node == null) { ArrayNode created = objectMapper.createArrayNode(); parent.set(field, created); return created; }
        if (!node.isArray()) throw new IllegalArgumentException(field + " 必须是数组");
        return (ArrayNode) node;
    }

    private CandidateProfileResponse emptyResponse(String profileId) {
        return new CandidateProfileResponse(profileId, defaultName(profileId), null, null, DEFAULT_PROFILE_ID.equals(profileId),
                SCHEMA_VERSION, 0, emptyContent(profileId), null);
    }

    private ObjectNode emptyContent(String profileId) {
        ObjectNode content = objectMapper.createObjectNode();
        content.put("schemaVersion", SCHEMA_VERSION); content.put("profileId", profileId);
        content.set("basic", objectMapper.createObjectNode()); content.set("contact", objectMapper.createObjectNode());
        content.set("education", objectMapper.createArrayNode()); content.set("fieldMeta", objectMapper.createObjectNode());
        return content;
    }

    private CandidateProfileResponse toResponse(CandidateProfile profile) {
        return new CandidateProfileResponse(profile.getProfileId(), profileName(profile), profile.getProfileDescription(), profile.getSourceResumeId(),
                DEFAULT_PROFILE_ID.equals(profile.getProfileId()), profile.getSchemaVersion(), profile.getRevision(), parse(profile.getContentJson()), profile.getUpdatedTime());
    }

    private CandidateProfileSummary toSummary(CandidateProfile profile) {
        return new CandidateProfileSummary(profile.getProfileId(), profileName(profile), profile.getProfileDescription(), profile.getSourceResumeId(),
                DEFAULT_PROFILE_ID.equals(profile.getProfileId()), profile.getRevision(), profile.getUpdatedTime());
    }

    private void appendSnapshot(String profileId, int revision, String contentJson, String description) {
        ProfileSnapshot snapshot = new ProfileSnapshot(); snapshot.setProfileId(profileId); snapshot.setRevision(revision); snapshot.setContentJson(contentJson); snapshot.setProfileDescription(description); profileSnapshotMapper.insert(snapshot);
    }

    private String profileName(CandidateProfile profile) { return StringUtils.hasText(profile.getProfileName()) ? profile.getProfileName() : defaultName(profile.getProfileId()); }
    private String defaultName(String profileId) { return DEFAULT_PROFILE_ID.equals(profileId) ? "默认档案" : "未命名档案"; }
    private String resolveProfileId(String profileId) { return StringUtils.hasText(profileId) ? profileId.trim() : DEFAULT_PROFILE_ID; }
    private String trimToNull(String value) { return StringUtils.hasText(value) ? value.trim() : null; }
    private String serialize(JsonNode content) { try { return objectMapper.writeValueAsString(content); } catch (JsonProcessingException e) { throw new IllegalArgumentException("个人档案序列化失败"); } }
    private JsonNode parse(String content) { try { return objectMapper.readTree(content); } catch (JsonProcessingException e) { throw new IllegalStateException("已保存的个人档案格式无效"); } }
}
