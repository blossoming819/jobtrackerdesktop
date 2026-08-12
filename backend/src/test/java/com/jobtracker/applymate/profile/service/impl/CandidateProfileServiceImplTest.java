package com.jobtracker.applymate.profile.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobtracker.applymate.profile.entity.CandidateProfile;
import com.jobtracker.applymate.profile.entity.ProfileSnapshot;
import com.jobtracker.applymate.profile.mapper.CandidateProfileMapper;
import com.jobtracker.applymate.profile.mapper.ProfileSnapshotMapper;
import com.jobtracker.mapper.JobApplicationMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CandidateProfileServiceImplTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void unchangedContentDoesNotCreateRevisionOrSnapshot() throws Exception {
        CandidateProfileMapper profileMapper = mock(CandidateProfileMapper.class);
        ProfileSnapshotMapper snapshotMapper = mock(ProfileSnapshotMapper.class);
        CandidateProfile existing = new CandidateProfile();
        existing.setId(1L);
        existing.setProfileId("default");
        existing.setProfileName("默认档案");
        existing.setSchemaVersion("0.1");
        existing.setRevision(7);
        existing.setContentJson("{\"schemaVersion\":\"0.1\",\"profileId\":\"default\",\"basic\":{\"nameCn\":\"测试用户\"},\"contact\":{},\"education\":[],\"fieldMeta\":{}}");
        when(profileMapper.selectOne(any())).thenReturn(existing);
        CandidateProfileServiceImpl service = new CandidateProfileServiceImpl(profileMapper, snapshotMapper, mock(JobApplicationMapper.class), objectMapper);
        JsonNode sameContent = objectMapper.readTree(existing.getContentJson());

        var response = service.save("default", sameContent);

        assertEquals(7, response.revision());
        verify(profileMapper, never()).updateById(any(CandidateProfile.class));
        verify(snapshotMapper, never()).insert(any(ProfileSnapshot.class));
    }
}
