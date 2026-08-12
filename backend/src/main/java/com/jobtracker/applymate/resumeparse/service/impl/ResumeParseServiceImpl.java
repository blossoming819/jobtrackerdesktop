package com.jobtracker.applymate.resumeparse.service.impl;

import com.jobtracker.applymate.resumeparse.dto.ResumeParseResponse;
import com.jobtracker.applymate.resumeparse.dto.ResumeParseRequest;
import com.jobtracker.applymate.resumeparse.dto.ResumeParseHistoryResponse;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jobtracker.applymate.ai.OpenAiCompatibleLlmClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jobtracker.applymate.resumeparse.entity.ResumeParseRecord;
import com.jobtracker.applymate.resumeparse.mapper.ResumeParseRecordMapper;
import com.jobtracker.applymate.resumeparse.service.ResumeParseService;
import com.jobtracker.applymate.resumeparse.service.ResumeTextExtractor;
import com.jobtracker.entity.Resume;
import com.jobtracker.service.ResumeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.function.Consumer;
import java.util.List;

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
            draft = normalizeDraft(draft);
            validateDraft(draft);
            status = "DRAFT_READY";
            record.setResultJson(writeJson(draft));
        }
        record.setStatus(status);
        record.setErrorCode(null);
        resumeParseRecordMapper.insert(record);
        pruneParseRecords(resumeId);
        return new ResumeParseResponse(record.getId(), resumeId, record.getStatus(), text.length(), "VISION_RECOMMENDED".equals(record.getStatus()), draft);
    }

    @Override
    public ResumeParseResponse extractStreaming(Long resumeId, ResumeParseRequest request, String providerId, Consumer<String> onDelta) {
        Resume resume = resumeService.getById(resumeId);
        if (resume == null) throw new IllegalArgumentException("简历不存在");
        Path path = Path.of(resume.getFilePath()).toAbsolutePath().normalize();
        if (!Files.isRegularFile(path)) throw new IllegalArgumentException("简历文件不存在或不可读取");
        String text = resumeTextExtractor.extract(path).trim();
        if (text.length() < 120) throw new IllegalArgumentException("VISION_RECOMMENDED: 提取文本过少，暂不适合文本模型解析");

        ResumeParseRecord record = new ResumeParseRecord();
        record.setResumeId(resumeId);
        record.setContentHash(sha256(path));
        record.setExtractor("APACHE_TIKA_3.3.2+LLM_STREAM");
        record.setExtractedLength(text.length());
        try {
            JsonNode draft = llmClient.generateJsonStreaming("resume-parse", providerId, systemPrompt(), "Resume text:\n" + text, false, onDelta);
            draft = normalizeDraft(draft);
            validateDraft(draft);
            record.setStatus("DRAFT_READY");
            record.setResultJson(writeJson(draft));
            record.setErrorCode(null);
            resumeParseRecordMapper.insert(record);
            pruneParseRecords(resumeId);
            return new ResumeParseResponse(record.getId(), resumeId, record.getStatus(), text.length(), false, draft);
        } catch (RuntimeException exception) {
            record.setStatus("FAILED");
            record.setErrorCode(exception.getMessage());
            resumeParseRecordMapper.insert(record);
            pruneParseRecords(resumeId);
            throw exception;
        }
    }

    @Override
    @Transactional
    public List<ResumeParseHistoryResponse> history(Long resumeId, int limit) {
        if (resumeService.getById(resumeId) == null) throw new IllegalArgumentException("简历不存在");
        pruneParseRecords(resumeId);
        return resumeParseRecordMapper.selectList(new LambdaQueryWrapper<ResumeParseRecord>()
                        .eq(ResumeParseRecord::getResumeId, resumeId)
                        .orderByDesc(ResumeParseRecord::getId)
                        .last("LIMIT " + Math.max(1, Math.min(limit, 50))))
                .stream()
                .map(record -> new ResumeParseHistoryResponse(record.getId(), record.getResumeId(), record.getStatus(),
                        record.getExtractedLength() == null ? 0 : record.getExtractedLength(), record.getExtractor(),
                        record.getErrorCode(), record.getCreatedTime(), parseResult(record.getResultJson())))
                .toList();
    }

    private void pruneParseRecords(Long resumeId) {
        List<ResumeParseRecord> records = resumeParseRecordMapper.selectList(new LambdaQueryWrapper<ResumeParseRecord>()
                .eq(ResumeParseRecord::getResumeId, resumeId)
                .orderByDesc(ResumeParseRecord::getId));
        Long latestLocalId = null;
        Long latestDraftId = null;
        for (ResumeParseRecord record : records) {
            if (latestDraftId == null && "DRAFT_READY".equals(record.getStatus())) latestDraftId = record.getId();
            if (latestLocalId == null && ("TEXT_EXTRACTED".equals(record.getStatus()) || "VISION_RECOMMENDED".equals(record.getStatus()))) latestLocalId = record.getId();
        }
        for (ResumeParseRecord record : records) {
            if (!record.getId().equals(latestLocalId) && !record.getId().equals(latestDraftId)) resumeParseRecordMapper.deleteById(record.getId());
        }
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

    private String systemPrompt() {
        return """
                You are the resume-to-profile extraction engine for ApplyMate. Convert the supplied resume text into ONE strict JSON object that can be reviewed and imported into a candidate profile.

                NON-NEGOTIABLE RULES
                1. Use only facts explicitly present in the resume, except for the narrowly defined education defaults below. Never infer age, gender, city, dates, rankings, technologies, duties, metrics, award level, project role, or award descriptions from general knowledge.
                2. Do not rewrite facts to sound better. Preserve names, numbers, links, technologies and quantified outcomes. You may only remove layout noise and join lines that clearly belong to the same bullet.
                3. Omit an unknown property; do not use placeholders such as unknown, N/A, -, null, empty objects, or invented values.
                4. Never output identity documents, passwords, verification codes, private keys, bank data, referral codes, photos or unrelated page text.
                5. Return JSON only, without Markdown fences, comments or explanatory prose.
                6. Every array item must represent exactly one real resume entry. Keep entries in resume order. Never merge two schools, employers, projects or awards.
                7. Distinguish company/work experience from projects: an employer and job title belong to work; a named product, system, research or course project belongs to projects, even when completed during employment.
                8. Distinguish responsibilities from achievements: responsibilities describe actions/scope; achievements contain results, metrics, awards or impact. Do not fabricate metrics.

                EXACT OUTPUT SHAPE
                {
                  "basic": {"nameCn":"", "nameEn":"", "gender":"MALE|FEMALE|UNSPECIFIED", "birthDate":"YYYY-MM-DD"},
                  "contact": {"phone":"", "email":"", "wechat":"", "currentCity":""},
                  "education": [{"id":"edu_1", "school":"", "college":"", "major":"", "degree":"BACHELOR|MASTER|DOCTOR", "startDate":"YYYY-MM|YYYY-MM-DD", "expectedGraduation":"YYYY-MM|YYYY-MM-DD", "studyMode":"FULL_TIME|PART_TIME", "gpa":""}],
                  "work": [{"id":"work_1", "company":"", "role":"", "department":"", "city":"", "startDate":"YYYY-MM|YYYY-MM-DD", "endDate":"YYYY-MM|YYYY-MM-DD", "employmentType":"INTERNSHIP|FULL_TIME|PART_TIME", "industry":"", "responsibilities":"", "achievements":"", "technologies":""}],
                  "projects": [{"id":"project_1", "name":"", "role":"", "startDate":"YYYY-MM|YYYY-MM-DD", "endDate":"YYYY-MM|YYYY-MM-DD", "type":"", "teamSize":"", "url":"", "technologies":"", "description":"", "contributions":""}],
                  "honors": [{"id":"honor_1", "name":"", "level":"", "date":"YYYY-MM|YYYY-MM-DD", "category":"COMPETITION|HONOR|SCHOLARSHIP|CERTIFICATE", "description":""}],
                  "skills": [{"name":"", "level":"", "description":""}]
                }

                NORMALIZATION
                - Education means higher education only. Do not emit high school entries.
                - Degree direct evidence has priority: 本科/学士 -> BACHELOR; 硕士/硕士研究生 -> MASTER; 博士/博士研究生 -> DOCTOR.
                - When a degree label is missing, infer only from an unambiguous higher-education progression. Order entries by actual start date from earliest to latest: the earliest stage is BACHELOR, the next is MASTER, and the next is DOCTOR. In resumes displayed newest-first this normally means the bottom/last education entry is BACHELOR, the entry above it MASTER, and the next above it DOCTOR. Never assign two different stages to one entry and never override an explicit degree.
                - studyMode: explicit 非全日制/在职/成人教育/自考 -> PART_TIME; explicit 全日制/统招 -> FULL_TIME. If it is a normal university degree entry and there is no contrary wording, use FULL_TIME as an unconfirmed application-form default.
                - Gender must be omitted unless explicitly stated. Do not derive it from a name or photo.
                - For work.employmentType, use FULL_TIME/PART_TIME only when explicitly stated. Use INTERNSHIP only for an explicit internship.
                - Preserve available date precision. Never invent a day. Normalize 2024.09, 2024/09 and 2024年9月 to 2024-09. Keep YYYY-MM-DD only when a day is present.
                - “至今/Present” may be represented by omitting endDate. Do not replace it with today's date.
                - technologies is a concise comma-separated string of technologies explicitly associated with that entry, not every skill in the resume.
                - Generate deterministic item ids by section order: edu_1, work_1, project_1, honor_1.
                - For an award or competition, preserve the exact award name, level, date, organizer and project information found in the resume. If description or organizer is absent, omit description. Online enrichment must be handled separately with verifiable sources; do not supply background knowledge yourself.
                - Keep the JSON compact. Do not emit fieldMeta; the server generates it deterministically after validation.

                FINAL SELF-CHECK BEFORE OUTPUT
                - JSON parses successfully and contains no keys outside the shape above.
                - Required item names exist: education.school, work.company, projects.name, honors.name.
                - Each fact can be traced to exact resume text.
                - For every date range, startDate must be earlier than or equal to endDate/expectedGraduation. Re-read the source line if they appear reversed.
                - Dates, enums and collection boundaries agree with the emitted data.
                - If a fact is ambiguous, omit it rather than guess.
                """;
    }

    private JsonNode normalizeDraft(JsonNode source) {
        if (source == null || !source.isObject()) return source;
        ObjectNode draft = (ObjectNode) source;
        normalizeEducation(draft.withArray("education"));
        ObjectNode fieldMeta = objectMapper.createObjectNode();
        addFieldMeta(draft, "", fieldMeta);
        draft.set("fieldMeta", fieldMeta);
        return draft;
    }

    private void normalizeEducation(ArrayNode education) {
        java.util.List<ObjectNode> entries = new java.util.ArrayList<>();
        for (JsonNode node : education) {
            if (!(node instanceof ObjectNode entry)) continue;
            String start = text(entry, "startDate");
            String end = text(entry, "expectedGraduation");
            if (!start.isBlank() && !end.isBlank() && compareIsoDate(start, end) > 0) {
                entry.put("startDate", end);
                entry.put("expectedGraduation", start);
            }
            if (!entry.hasNonNull("studyMode") || entry.path("studyMode").asText().isBlank()) entry.put("studyMode", "FULL_TIME");
            entries.add(entry);
        }

        java.util.List<ObjectNode> chronological = entries.stream()
                .filter(entry -> !text(entry, "startDate").isBlank())
                .sorted(java.util.Comparator.comparing(entry -> text(entry, "startDate")))
                .toList();
        String[] ladder = {"BACHELOR", "MASTER", "DOCTOR"};
        if (chronological.size() >= 2 && chronological.size() <= ladder.length) {
            boolean compatible = true;
            for (int index = 0; index < chronological.size(); index++) {
                String explicit = text(chronological.get(index), "degree");
                if (!explicit.isBlank() && !explicit.equals(ladder[index])) compatible = false;
            }
            if (compatible) {
                for (int index = 0; index < chronological.size(); index++) {
                    ObjectNode entry = chronological.get(index);
                    if (!entry.hasNonNull("degree") || entry.path("degree").asText().isBlank()) entry.put("degree", ladder[index]);
                }
            }
        }
    }

    private int compareIsoDate(String left, String right) {
        return left.substring(0, Math.min(10, left.length())).compareTo(right.substring(0, Math.min(10, right.length())));
    }

    private String text(ObjectNode node, String field) {
        return node.path(field).isTextual() ? node.path(field).asText().trim() : "";
    }

    private void addFieldMeta(JsonNode node, String path, ObjectNode target) {
        if (node == null || node.isNull() || "fieldMeta".equals(path)) return;
        if (node.isObject()) {
            node.fields().forEachRemaining(entry -> {
                if (!"fieldMeta".equals(entry.getKey())) addFieldMeta(entry.getValue(), joinPath(path, entry.getKey()), target);
            });
            return;
        }
        if (node.isArray()) {
            for (int index = 0; index < node.size(); index++) addFieldMeta(node.get(index), joinPath(path, String.valueOf(index)), target);
            return;
        }
        if (path.isBlank() || path.matches("(?:education|work|projects|honors)\\.\\d+\\.id")) return;
        ObjectNode meta = objectMapper.createObjectNode();
        meta.put("source", "RESUME");
        meta.put("confirmationState", "UNCONFIRMED");
        meta.put("sensitivity", path.startsWith("basic.") || path.startsWith("contact.") ? "PERSONAL" : "NORMAL");
        target.set(path, meta);
    }

    private String joinPath(String prefix, String name) { return prefix.isBlank() ? name : prefix + "." + name; }
    private void validateDraft(JsonNode draft) {
        if (draft == null || !draft.isObject()) throw new IllegalArgumentException("AI_OUTPUT_INVALID: root must be an object");
        java.util.Set<String> allowed = java.util.Set.of("basic", "contact", "education", "work", "projects", "honors", "skills", "fieldMeta");
        draft.fieldNames().forEachRemaining(name -> { if (!allowed.contains(name)) throw new IllegalArgumentException("AI_OUTPUT_INVALID: unsupported root field " + name); });
        for (String name : java.util.List.of("basic", "contact", "fieldMeta")) {
            if (draft.has(name) && !draft.path(name).isObject()) throw new IllegalArgumentException("AI_OUTPUT_INVALID: " + name + " must be an object");
        }
        validateItems(draft, "education", "school");
        validateItems(draft, "work", "company");
        validateItems(draft, "projects", "name");
        validateItems(draft, "honors", "name");
        if (draft.has("skills") && !draft.path("skills").isArray()) throw new IllegalArgumentException("AI_OUTPUT_INVALID: skills must be an array");
        validateEnum(draft.path("basic").path("gender"), java.util.Set.of("MALE", "FEMALE", "UNSPECIFIED"), "basic.gender");
        for (JsonNode education : draft.path("education")) {
            validateEnum(education.path("degree"), java.util.Set.of("BACHELOR", "MASTER", "DOCTOR"), "education.degree");
            validateEnum(education.path("studyMode"), java.util.Set.of("FULL_TIME", "PART_TIME"), "education.studyMode");
        }
        for (JsonNode work : draft.path("work")) validateEnum(work.path("employmentType"), java.util.Set.of("INTERNSHIP", "FULL_TIME", "PART_TIME"), "work.employmentType");
        for (JsonNode honor : draft.path("honors")) validateEnum(honor.path("category"), java.util.Set.of("COMPETITION", "HONOR", "SCHOLARSHIP", "CERTIFICATE"), "honors.category");
    }
    private void validateItems(JsonNode root, String section, String requiredName) {
        if (!root.has(section)) return;
        JsonNode items = root.path(section);
        if (!items.isArray()) throw new IllegalArgumentException("AI_OUTPUT_INVALID: " + section + " must be an array");
        for (JsonNode item : items) if (!item.isObject() || !item.path(requiredName).isTextual() || item.path(requiredName).asText().isBlank())
            throw new IllegalArgumentException("AI_OUTPUT_INVALID: " + section + "." + requiredName + " is required");
    }
    private void validateEnum(JsonNode value, java.util.Set<String> allowed, String path) {
        if (!value.isMissingNode() && !value.isNull() && (!value.isTextual() || !allowed.contains(value.asText())))
            throw new IllegalArgumentException("AI_OUTPUT_INVALID: invalid " + path);
    }
    private String writeJson(JsonNode node) { try { return objectMapper.writeValueAsString(node); } catch (Exception e) { throw new IllegalArgumentException("AI_OUTPUT_INVALID"); } }
    private JsonNode parseResult(String value) { try { return value == null || value.isBlank() ? null : objectMapper.readTree(value); } catch (Exception e) { return null; } }
}
