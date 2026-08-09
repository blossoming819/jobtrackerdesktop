package com.jobtracker.applymate.resumeparse.service.impl;

import com.jobtracker.applymate.resumeparse.dto.ResumeParseResponse;
import com.jobtracker.applymate.resumeparse.dto.ResumeParseRequest;
import com.jobtracker.applymate.ai.OpenAiCompatibleLlmClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobtracker.applymate.resumeparse.entity.ResumeParseRecord;
import com.jobtracker.applymate.resumeparse.mapper.ResumeParseRecordMapper;
import com.jobtracker.applymate.resumeparse.service.ResumeParseService;
import com.jobtracker.applymate.resumeparse.service.ResumeTextExtractor;
import com.jobtracker.entity.Resume;
import com.jobtracker.service.ResumeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

@Service
@RequiredArgsConstructor
public class ResumeParseServiceImpl implements ResumeParseService {
    private final ResumeService resumeService;
    private final ResumeTextExtractor resumeTextExtractor;
    private final ResumeParseRecordMapper resumeParseRecordMapper;
    private final OpenAiCompatibleLlmClient llmClient;
    private final ObjectMapper objectMapper;

    @Override
    public ResumeParseResponse extract(Long resumeId, ResumeParseRequest request) {
        Resume resume = resumeService.getById(resumeId);
        if (resume == null) throw new IllegalArgumentException("简历不存在");
        Path path = Path.of(resume.getFilePath()).toAbsolutePath().normalize();
        if (!Files.isRegularFile(path)) throw new IllegalArgumentException("简历文件不存在或不可读取");

        String text = resumeTextExtractor.extract(path).trim();
        ResumeParseRecord record = new ResumeParseRecord();
        record.setResumeId(resumeId);
        record.setContentHash(sha256(path));
        record.setExtractor("APACHE_TIKA_3.3.2");
        record.setExtractedLength(text.length());
        JsonNode draft = null;
        String status = text.length() < 120 ? "VISION_RECOMMENDED" : "TEXT_EXTRACTED";
        if (request.allowCloudAi() && (!request.allowVisionFallback() || text.length() >= 120)) {
            draft = llmClient.generateJson("resume-parse", systemPrompt(), "Resume text:\n" + text, false);
            status = "DRAFT_READY";
            record.setResultJson(writeJson(draft));
        }
        record.setStatus(status);
        record.setErrorCode(null);
        resumeParseRecordMapper.insert(record);
        return new ResumeParseResponse(record.getId(), resumeId, record.getStatus(), text.length(), "VISION_RECOMMENDED".equals(record.getStatus()), draft);
    }

    private String sha256(Path path) {
        try {
            byte[] bytes = Files.readAllBytes(path);
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder value = new StringBuilder(64);
            for (byte item : digest) value.append(String.format("%02x", item));
            return value.toString();
        } catch (IOException | NoSuchAlgorithmException e) {
            throw new IllegalArgumentException("简历文件校验失败");
        }
    }

    private String systemPrompt() { return "Extract only explicitly supported candidate facts from the resume. Return JSON only. Do not invent data. Use fields basic, contact, education, work, projects, skills and fieldMeta; unknown values must be omitted."; }
    private String writeJson(JsonNode node) { try { return objectMapper.writeValueAsString(node); } catch (Exception e) { throw new IllegalArgumentException("AI_OUTPUT_INVALID"); } }
}
