package com.jobtracker.applymate.security;

import com.jobtracker.common.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;

@RestController @RequiredArgsConstructor @RequestMapping("/api/applymate/v1/pairing")
public class PairingController {
  private final ExtensionPairingMapper mapper;
  @PostMapping("/requests") public Result<RequestResult> request(@RequestBody Request request) {
    if (request.extensionId() == null || request.extensionId().isBlank()) throw new IllegalArgumentException("extensionId 不能为空");
    ExtensionPairing record = new ExtensionPairing(); record.setExtensionId(request.extensionId()); record.setDisplayName(request.displayName()); record.setStatus("PENDING"); mapper.insert(record);
    return Result.ok(new RequestResult(record.getId(), "PENDING"));
  }
  // This endpoint is intentionally for the local desktop UI only; CORS hardening follows in the next change.
  @PostMapping("/{id}/approve") public Result<TokenResult> approve(@PathVariable Long id) {
    ExtensionPairing record = mapper.selectById(id); if (record == null || !"PENDING".equals(record.getStatus())) throw new IllegalArgumentException("配对请求不存在或已处理");
    String token = token(); record.setTokenHash(hash(token)); record.setStatus("APPROVED"); record.setApprovedTime(LocalDateTime.now()); mapper.updateById(record);
    return Result.ok(new TokenResult(record.getId(), token));
  }
  public record Request(String extensionId, String displayName) {} public record RequestResult(Long requestId, String status) {} public record TokenResult(Long requestId, String token) {}
  private String token() { byte[] b = new byte[32]; new SecureRandom().nextBytes(b); return Base64.getUrlEncoder().withoutPadding().encodeToString(b); }
  private String hash(String value) { try { byte[] d = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)); StringBuilder s=new StringBuilder(); for(byte b:d)s.append(String.format("%02x",b)); return s.toString(); } catch(Exception e){ throw new IllegalStateException(e); } }
}
