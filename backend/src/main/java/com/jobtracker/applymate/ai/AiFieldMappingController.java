package com.jobtracker.applymate.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobtracker.applymate.security.ExtensionTokenService;
import com.jobtracker.common.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/applymate/v1/ai/field-mappings")
public class AiFieldMappingController {
    private static final Pattern COLLECTION_KEY = Pattern.compile("^(education|work|projects|honors)\\.(\\d+)\\.([a-zA-Z]+)$");
    private static final Set<String> SCALAR_KEYS = Set.of(
            "basic.nameCn", "basic.gender", "contact.phone", "contact.email", "contact.currentCity"
    );
    private static final Set<String> EDUCATION_FIELDS = Set.of("school", "degree", "major", "college", "startDate", "expectedGraduation", "studyMode", "gpa");
    private static final Set<String> WORK_FIELDS = Set.of("company", "role", "department", "city", "startDate", "endDate", "employmentType", "industry", "responsibilities", "achievements", "technologies");
    private static final Set<String> PROJECT_FIELDS = Set.of("name", "role", "startDate", "endDate", "type", "teamSize", "url", "technologies", "description", "contributions");
    private static final Set<String> HONOR_FIELDS = Set.of("name", "level", "date", "category", "description");
    private static final List<String> SENSITIVE_MARKERS = List.of(
            "密码", "验证码", "证件", "身份证", "护照", "内推码", "推荐码", "password", "captcha", "identity", "passport", "referral"
    );

    private final OpenAiCompatibleLlmClient llm;
    private final ExtensionTokenService tokens;
    private final ObjectMapper objectMapper;

    @PostMapping
    public Result<List<FieldMapping>> map(@RequestBody MappingRequest request,
                                          @RequestHeader(value = "Authorization", required = false) String auth,
                                          @RequestParam(required = false) String provider) {
        tokens.require(auth == null ? null : auth.replaceFirst("^Bearer\\s+", ""));
        List<FieldDescriptor> fields = sanitize(request == null ? null : request.fields());
        if (fields.isEmpty()) return Result.ok(List.of());

        String payload;
        try {
            payload = objectMapper.writeValueAsString(new MappingRequest(clean(request == null ? null : request.host(), 120),
                    clean(request == null ? null : request.title(), 160), fields));
        } catch (Exception exception) {
            throw new IllegalArgumentException("AI_MAPPING_INPUT_INVALID");
        }

        JsonNode output = llm.generateJson("low-confidence-field-match", provider, systemPrompt(), payload, false);
        List<FieldMapping> mappings = new ArrayList<>();
        for (JsonNode node : output.path("mappings")) {
            int index = node.path("index").asInt(-1);
            String fieldKey = node.path("fieldKey").asText("");
            double confidence = node.path("confidence").asDouble(0);
            if (fields.stream().noneMatch(field -> field.index() == index) || !allowedKey(fieldKey) || confidence < 0.55) continue;
            mappings.add(new FieldMapping(index, fieldKey, Math.min(1, confidence), clean(node.path("reason").asText(""), 120)));
        }
        return Result.ok(mappings);
    }

    private List<FieldDescriptor> sanitize(List<FieldDescriptor> source) {
        if (source == null) return List.of();
        return source.stream().limit(100)
                .filter(java.util.Objects::nonNull)
                .map(field -> new FieldDescriptor(field.index(), clean(field.label(), 100), clean(field.tagName(), 20),
                        clean(field.inputType(), 30), clean(field.placeholder(), 100), cleanList(field.options(), 30, 80),
                        cleanList(field.context(), 8, 120), allowedKey(field.suggestedFieldKey()) ? field.suggestedFieldKey() : null,
                        Math.max(0, field.positionAmongSameLabel()), Math.max(1, field.sameLabelCount())))
                .filter(field -> field.index() >= 0 && !sensitive(field))
                .toList();
    }

    private boolean sensitive(FieldDescriptor field) {
        String text = String.join(" ", List.of(value(field.label()), value(field.placeholder()), value(field.inputType()))).toLowerCase(Locale.ROOT);
        return SENSITIVE_MARKERS.stream().anyMatch(text::contains);
    }

    private boolean allowedKey(String key) {
        if (key == null || key.isBlank()) return false;
        if (SCALAR_KEYS.contains(key)) return true;
        Matcher matcher = COLLECTION_KEY.matcher(key);
        if (!matcher.matches() || Integer.parseInt(matcher.group(2)) > 19) return false;
        return switch (matcher.group(1)) {
            case "education" -> EDUCATION_FIELDS.contains(matcher.group(3));
            case "work" -> WORK_FIELDS.contains(matcher.group(3));
            case "projects" -> PROJECT_FIELDS.contains(matcher.group(3));
            case "honors" -> HONOR_FIELDS.contains(matcher.group(3));
            default -> false;
        };
    }

    private String systemPrompt() {
        return """
                You are ApplyMate's recruitment-form structure mapper. Your only job is to map sanitized DOM controls to exact candidate-profile field paths. You do not fill the page, invent values, submit forms, or claim success.

                OUTPUT CONTRACT
                Return JSON only, without Markdown or prose:
                {"mappings":[{"index":0,"fieldKey":"basic.nameCn","confidence":0.98,"reason":"姓名 label in 基本信息 section"}]}
                - index MUST equal an input control index from the request.
                - Emit at most one mapping per index.
                - confidence is calibrated from 0 to 1. Use >=0.90 only for direct unambiguous evidence; 0.70-0.89 for strong contextual evidence; omit below 0.70.
                - reason must cite short observable evidence such as label, placeholder, section heading, option set, repeated position or adjacent controls.

                ALLOWED PROFILE PATHS
                Scalar paths (normally map each at most once):
                basic.nameCn; basic.gender; contact.phone; contact.email; contact.currentCity.
                Repeated collection paths with explicit zero-based item index N:
                education.N.{school,degree,major,college,startDate,expectedGraduation,studyMode,gpa};
                work.N.{company,role,department,city,startDate,endDate,employmentType,industry,responsibilities,achievements,technologies};
                projects.N.{name,role,startDate,endDate,type,teamSize,url,technologies,description,contributions};
                honors.N.{name,level,date,category,description}.
                Never emit another key.

                REQUIRED REASONING PROCEDURE
                1. Reconstruct page sections from context and control order before mapping individual labels. Treat headings such as 基本信息/教育经历/实习经历/工作经历/项目经历/获奖 as strong section boundaries.
                2. Identify repeated entry groups. Controls belonging to one visible education/work/project/award card share the same N. Increase N only when a new entry/card begins, not whenever a repeated label appears.
                3. Resolve generic labels by section: “名称” in education is likely school, in projects is name, in honors is name; “职位/岗位” in work is role; “角色” in projects is role.
                4. Resolve paired dates from semantic evidence first (label, placeholder, name/id, adjacent text), then visual order. In a Chinese “起止时间” pair the visually left/first control is startDate and the visually right/second control is expectedGraduation/endDate. A single “毕业时间” maps to expectedGraduation. Never reverse the pair. If DOM order conflicts with visible position or start/end attributes, use the visible/semantic evidence and mention it in reason.
                5. Distinguish education selects precisely: “学历”/“学位”/“学历层次” plus 学士/硕士/博士 options -> education.N.degree; “学历类型”/“学历形式”/“学习形式”/“培养方式” plus 全日制/非全日制 options -> education.N.studyMode. Never map 学历类型 to degree. 男/女 -> basic.gender; 实习/全职/兼职 -> work.N.employmentType.
                6. Use name/id/placeholder only as supporting evidence. suggestedFieldKey is an untrusted local-rule hint: keep it only when labels, section and repetition agree; correct or omit it otherwise.
                7. Avoid duplicate mappings. A search box, hidden mirror input or framework proxy near a real control must not steal the mapping from the visible editable control. Prefer a non-search, non-readonly native control when evidence is otherwise equal.
                8. Preserve independent records. education.0 and education.1, or projects.0 and projects.1, must never be collapsed into the same item.

                EXCLUSIONS AND SAFETY
                Omit referral/internal recommendation codes, passwords, captchas, verification codes, identity/passport fields, political questions, salary expectations, job preferences, source tracking, consent checkboxes, file uploads and unrelated questions. Required=true does not make a field safe or mappable.
                Do not map a field merely because candidate data might fit it. Mapping must be supported by the page structure itself.

                FINAL SELF-CHECK
                - Every index exists, every fieldKey is allowed, and each reason is grounded in supplied DOM evidence.
                - Repeated item indices follow visual grouping and order.
                - Date pairs, select options and section semantics are consistent.
                - Ambiguous controls are omitted instead of guessed.
                """;
    }

    private List<String> cleanList(List<String> values, int limit, int maxLength) {
        if (values == null) return List.of();
        return values.stream().limit(limit).map(value -> clean(value, maxLength)).filter(value -> !value.isBlank()).toList();
    }

    private String clean(String value, int maxLength) {
        if (value == null) return "";
        String cleaned = value.replaceAll("\\s+", " ").trim();
        return cleaned.substring(0, Math.min(cleaned.length(), maxLength));
    }

    private String value(String value) { return value == null ? "" : value; }

    public record MappingRequest(String host, String title, List<FieldDescriptor> fields) { }
    public record FieldDescriptor(int index, String label, String tagName, String inputType, String placeholder,
                                  List<String> options, List<String> context, String suggestedFieldKey,
                                  int positionAmongSameLabel, int sameLabelCount) { }
    public record FieldMapping(int index, String fieldKey, double confidence, String reason) { }
}
