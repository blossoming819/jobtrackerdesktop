package com.jobtracker.applymate.profile.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jobtracker.applymate.profile.service.CandidateProfileService;
import com.jobtracker.applymate.security.ExtensionTokenService;
import com.jobtracker.common.Result;
import com.jobtracker.entity.JobApplication;
import com.jobtracker.service.JobApplicationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController @RequiredArgsConstructor @RequestMapping("/api/applymate/v1/profile/values")
public class ProfileValueController {
  private final CandidateProfileService profiles; private final ExtensionTokenService tokens; private final JobApplicationService applications;
  @GetMapping public Result<Map<String,Object>> values(@RequestParam List<String> keys, @RequestParam(required=false) String profileId, @RequestHeader(value="Authorization",required=false) String auth) {
    tokens.require(auth == null ? null : auth.replaceFirst("^Bearer\\s+", ""));
    JsonNode root=profiles.get(profileId).content(); Map<String,Object> result=new LinkedHashMap<>();
    for(String key:keys) { if(key.startsWith("identity.") || key.contains("identityCard") || key.contains("password")) continue; JsonNode value=read(root,key); if(value!=null&&!value.isMissingNode()&&!value.isNull()) result.put(key,value); }
    return Result.ok(result);
  }
  @GetMapping("/versions") public Result<java.util.List<com.jobtracker.applymate.profile.dto.CandidateProfileSummary>> versions(@RequestHeader(value="Authorization",required=false) String auth) {
    tokens.require(auth == null ? null : auth.replaceFirst("^Bearer\\s+", ""));
    return Result.ok(profiles.versions());
  }
  @GetMapping("/context") public Result<ProfileContext> context(@RequestParam String url, @RequestHeader(value="Authorization",required=false) String auth) {
    tokens.require(auth == null ? null : auth.replaceFirst("^Bearer\\s+", ""));
    JobApplication application = applications.lambdaQuery().eq(JobApplication::getJobLink, url).isNotNull(JobApplication::getProfileId).orderByDesc(JobApplication::getId).last("LIMIT 1").one();
    if (application == null) return Result.ok(new ProfileContext(null, null, null, null));
    var profile = profiles.get(application.getProfileId());
    return Result.ok(new ProfileContext(application.getId(), application.getCompanyName(), profile.profileId(), profile.name()));
  }
  public record ProfileContext(Long applicationId, String companyName, String profileId, String profileName) {}
  private JsonNode read(JsonNode root,String key){
    JsonNode n=root;
    String[] parts=key.split("\\.");
    for(String part:parts){
      if(n != null && n.isArray()) {
        int index = part.matches("\\d+") ? Integer.parseInt(part) : 0;
        n = n.size() > index ? n.get(index) : null;
        if (part.matches("\\d+")) continue;
      }
      n=n==null?null:n.get(part);
    }
    return n;
  }
}
