package com.jobtracker.applymate.profile.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jobtracker.applymate.profile.service.CandidateProfileService;
import com.jobtracker.applymate.security.ExtensionTokenService;
import com.jobtracker.common.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController @RequiredArgsConstructor @RequestMapping("/api/applymate/v1/profile/values")
public class ProfileValueController {
  private final CandidateProfileService profiles; private final ExtensionTokenService tokens;
  @GetMapping public Result<Map<String,Object>> values(@RequestParam List<String> keys, @RequestHeader(value="Authorization",required=false) String auth) {
    tokens.require(auth == null ? null : auth.replaceFirst("^Bearer\\s+", ""));
    JsonNode root=profiles.current().content(); Map<String,Object> result=new LinkedHashMap<>();
    for(String key:keys) { if(key.startsWith("identity.") || key.contains("identityCard") || key.contains("password")) continue; JsonNode value=read(root,key); if(value!=null&&!value.isMissingNode()&&!value.isNull()) result.put(key,value); }
    return Result.ok(result);
  }
  private JsonNode read(JsonNode root,String key){
    JsonNode n=root;
    String[] parts=key.split("\\.");
    for(String part:parts){
      if(n != null && n.isArray()) n = n.isEmpty() ? null : n.get(0);
      n=n==null?null:n.get(part);
    }
    return n;
  }
}
