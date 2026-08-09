package com.jobtracker.applymate.profile.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jobtracker.applymate.profile.dto.CandidateProfileResponse;
import com.jobtracker.applymate.profile.dto.ProfileSnapshotResponse;
import com.jobtracker.applymate.profile.entity.CandidateProfile;
import com.jobtracker.applymate.profile.entity.ProfileSnapshot;
import com.jobtracker.applymate.profile.mapper.CandidateProfileMapper;
import com.jobtracker.applymate.profile.mapper.ProfileSnapshotMapper;
import com.jobtracker.applymate.profile.service.CandidateProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CandidateProfileServiceImpl implements CandidateProfileService {
    private static final String DEFAULT_PROFILE_ID = "default";
    private static final String SCHEMA_VERSION = "0.1";

    private final CandidateProfileMapper candidateProfileMapper;
    private final ProfileSnapshotMapper profileSnapshotMapper;
    private final ObjectMapper objectMapper;

    @Override
    public CandidateProfileResponse current() {
        CandidateProfile profile = findCurrent();
        return profile == null ? emptyResponse() : toResponse(profile);
    }

    @Override
    @Transactional
    public CandidateProfileResponse save(JsonNode content) {
        JsonNode normalized = normalizeAndValidate(content);
        CandidateProfile existing = findCurrent();
        int revision = existing == null ? 1 : existing.getRevision() + 1;
        String serialized = serialize(normalized);

        CandidateProfile profile = existing == null ? new CandidateProfile() : existing;
        profile.setProfileId(DEFAULT_PROFILE_ID);
        profile.setSchemaVersion(SCHEMA_VERSION);
        profile.setContentJson(serialized);
        profile.setRevision(revision);
        if (existing == null) {
            candidateProfileMapper.insert(profile);
        } else {
            candidateProfileMapper.updateById(profile);
        }

        ProfileSnapshot snapshot = new ProfileSnapshot();
        snapshot.setProfileId(DEFAULT_PROFILE_ID);
        snapshot.setRevision(revision);
        snapshot.setContentJson(serialized);
        profileSnapshotMapper.insert(snapshot);
        return toResponse(profile);
    }

    @Override
    public List<ProfileSnapshotResponse> snapshots() {
        return profileSnapshotMapper.selectList(new LambdaQueryWrapper<ProfileSnapshot>()
                        .eq(ProfileSnapshot::getProfileId, DEFAULT_PROFILE_ID)
                        .orderByDesc(ProfileSnapshot::getRevision))
                .stream()
                .map(snapshot -> new ProfileSnapshotResponse(snapshot.getId(), snapshot.getRevision(), snapshot.getCreatedTime()))
                .toList();
    }

    @Override
    public CandidateProfileResponse restore(Long snapshotId) {
        ProfileSnapshot snapshot = profileSnapshotMapper.selectById(snapshotId);
        if (snapshot == null || !DEFAULT_PROFILE_ID.equals(snapshot.getProfileId())) {
            throw new IllegalArgumentException("档案快照不存在");
        }
        return save(parse(snapshot.getContentJson()));
    }

    private CandidateProfile findCurrent() {
        return candidateProfileMapper.selectOne(new LambdaQueryWrapper<CandidateProfile>()
                .eq(CandidateProfile::getProfileId, DEFAULT_PROFILE_ID));
    }

    private JsonNode normalizeAndValidate(JsonNode content) {
        if (content == null || !content.isObject()) {
            throw new IllegalArgumentException("个人档案必须是 JSON 对象");
        }
        ObjectNode profile = ((ObjectNode) content).deepCopy();
        profile.put("schemaVersion", SCHEMA_VERSION);
        profile.put("profileId", DEFAULT_PROFILE_ID);
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
        if (node == null) {
            ObjectNode created = objectMapper.createObjectNode();
            parent.set(field, created);
            return created;
        }
        if (!node.isObject()) {
            throw new IllegalArgumentException(field + " 必须是对象");
        }
        return (ObjectNode) node;
    }

    private ArrayNode requireArray(ObjectNode parent, String field) {
        JsonNode node = parent.get(field);
        if (node == null) {
            ArrayNode created = objectMapper.createArrayNode();
            parent.set(field, created);
            return created;
        }
        if (!node.isArray()) {
            throw new IllegalArgumentException(field + " 必须是数组");
        }
        return (ArrayNode) node;
    }

    private CandidateProfileResponse emptyResponse() {
        ObjectNode content = objectMapper.createObjectNode();
        content.put("schemaVersion", SCHEMA_VERSION);
        content.put("profileId", DEFAULT_PROFILE_ID);
        content.set("basic", objectMapper.createObjectNode());
        content.set("contact", objectMapper.createObjectNode());
        content.set("education", objectMapper.createArrayNode());
        content.set("fieldMeta", objectMapper.createObjectNode());
        return new CandidateProfileResponse(DEFAULT_PROFILE_ID, SCHEMA_VERSION, 0, content, null);
    }

    private CandidateProfileResponse toResponse(CandidateProfile profile) {
        return new CandidateProfileResponse(profile.getProfileId(), profile.getSchemaVersion(), profile.getRevision(),
                parse(profile.getContentJson()), profile.getUpdatedTime());
    }

    private String serialize(JsonNode content) {
        try {
            return objectMapper.writeValueAsString(content);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("个人档案序列化失败");
        }
    }

    private JsonNode parse(String content) {
        try {
            return objectMapper.readTree(content);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("已保存的个人档案格式无效");
        }
    }
}
