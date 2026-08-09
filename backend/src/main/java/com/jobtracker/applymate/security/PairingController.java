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
    String secret=token(); ExtensionPairing record = new ExtensionPairing(); record.setExtensionId(request.extensionId()); record.setDisplayName(request.displayName()); record.setClaimSecretHash(hash(secret)); record.setStatus("PENDING"); mapper.insert(record);
    return Result.ok(new RequestResult(record.getId(), "PENDING", secret));
  }
  // This endpoint is intentionally for the local desktop UI only; CORS hardening follows in the next change.
  @PostMapping("/{id}/approve") public Result<RequestResult> approve(@PathVariable Long id) {
    ExtensionPairing record = mapper.selectById(id); if (record == null || !"PENDING".equals(record.getStatus())) throw new IllegalArgumentException("配对请求不存在或已处理");
    record.setStatus("APPROVED"); record.setApprovedTime(LocalDateTime.now()); mapper.updateById(record);
    return Result.ok(new RequestResult(record.getId(), "APPROVED", null));
  }
  @PostMapping("/{id}/claim") public Result<TokenResult> claim(@PathVariable Long id,@RequestBody Claim claim) { ExtensionPairing r=mapper.selectById(id); if(r==null||!"APPROVED".equals(r.getStatus())||!hash(claim.claimSecret()).equals(r.getClaimSecretHash()))throw new IllegalArgumentException("PAIRING_NOT_APPROVED"); String value=token();r.setTokenHash(hash(value));r.setStatus("CLAIMED");mapper.updateById(r);return Result.ok(new TokenResult(id,value)); }
  @GetMapping("/pending") public Result<java.util.List<ExtensionPairing>> pending(){return Result.ok(mapper.selectList(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ExtensionPairing>().eq(ExtensionPairing::getStatus,"PENDING")));}
  @GetMapping("/authorized") public Result<java.util.List<ExtensionPairing>> authorized(){return Result.ok(mapper.selectList(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ExtensionPairing>().eq(ExtensionPairing::getStatus,"CLAIMED")));}
  @DeleteMapping("/{id}") public Result<Void> revoke(@PathVariable Long id) { ExtensionPairing record = mapper.selectById(id); if (record == null || !"CLAIMED".equals(record.getStatus())) throw new IllegalArgumentException("已授权的扩展不存在"); record.setStatus("REVOKED"); record.setTokenHash(null); record.setClaimSecretHash(null); mapper.updateById(record); return Result.ok(); }
  public record Request(String extensionId, String displayName) {} public record Claim(String claimSecret) {} public record RequestResult(Long requestId, String status, String claimSecret) {} public record TokenResult(Long requestId, String token) {}
  private String token() { byte[] b = new byte[32]; new SecureRandom().nextBytes(b); return Base64.getUrlEncoder().withoutPadding().encodeToString(b); }
  private String hash(String value) { try { byte[] d = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)); StringBuilder s=new StringBuilder(); for(byte b:d)s.append(String.format("%02x",b)); return s.toString(); } catch(Exception e){ throw new IllegalStateException(e); } }
}
