package com.jobtracker.applymate.security;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.jobtracker.common.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@RestController @RequiredArgsConstructor @RequestMapping("/api/applymate/v1/pairing")
public class PairingController {
  private final ExtensionPairingMapper mapper;
  @PostMapping("/requests") public Result<RequestResult> request(@RequestBody Request request) {
    if (request.extensionId() == null || request.extensionId().isBlank()) throw new IllegalArgumentException("extensionId 不能为空");
    mapper.update(null, new LambdaUpdateWrapper<ExtensionPairing>()
        .eq(ExtensionPairing::getExtensionId, request.extensionId())
        .in(ExtensionPairing::getStatus, "PENDING", "APPROVED")
        .set(ExtensionPairing::getStatus, "REVOKED")
        .set(ExtensionPairing::getClaimSecretHash, null));
    String secret=token(); ExtensionPairing record = new ExtensionPairing(); record.setExtensionId(request.extensionId()); record.setDisplayName(request.displayName()); record.setClaimSecretHash(hash(secret)); record.setStatus("PENDING"); mapper.insert(record);
    return Result.ok(new RequestResult(record.getId(), "PENDING", secret));
  }
  // This endpoint is intentionally for the local desktop UI only; CORS hardening follows in the next change.
  @PostMapping("/{id}/approve") public Result<RequestResult> approve(@PathVariable Long id) {
    ExtensionPairing record = mapper.selectById(id); if (record == null || !"PENDING".equals(record.getStatus())) throw new IllegalArgumentException("配对请求不存在或已处理");
    record.setStatus("APPROVED"); record.setApprovedTime(LocalDateTime.now()); mapper.updateById(record);
    return Result.ok(new RequestResult(record.getId(), "APPROVED", null));
  }
  @PostMapping("/{id}/claim") public Result<TokenResult> claim(@PathVariable Long id,@RequestBody Claim claim) {
    ExtensionPairing record=mapper.selectById(id);
    if(record==null||!"APPROVED".equals(record.getStatus())||claim.claimSecret()==null||!hash(claim.claimSecret()).equals(record.getClaimSecretHash()))throw new IllegalArgumentException("PAIRING_NOT_APPROVED");
    revokeClaimed(record.getExtensionId());
    String value=token();
    mapper.update(null, new LambdaUpdateWrapper<ExtensionPairing>()
        .eq(ExtensionPairing::getId, id)
        .set(ExtensionPairing::getTokenHash, hash(value))
        .set(ExtensionPairing::getClaimSecretHash, null)
        .set(ExtensionPairing::getStatus, "CLAIMED"));
    return Result.ok(new TokenResult(id,value));
  }
  @GetMapping("/pending") public Result<List<PairingSummary>> pending(){return Result.ok(latestByExtension("PENDING"));}
  @GetMapping("/authorized") public Result<List<PairingSummary>> authorized(){return Result.ok(latestByExtension("CLAIMED"));}
  @DeleteMapping("/{id}") public Result<Void> revoke(@PathVariable Long id) {
    ExtensionPairing record = mapper.selectById(id);
    if (record == null || !"CLAIMED".equals(record.getStatus())) throw new IllegalArgumentException("已授权的扩展不存在");
    revokeClaimed(record.getExtensionId());
    return Result.ok();
  }
  public record Request(String extensionId, String displayName) {} public record Claim(String claimSecret) {} public record RequestResult(Long requestId, String status, String claimSecret) {} public record TokenResult(Long requestId, String token) {}
  public record PairingSummary(Long id, String extensionId, String displayName, String status, LocalDateTime createdTime, LocalDateTime approvedTime) {}
  private List<PairingSummary> latestByExtension(String status) {
    List<ExtensionPairing> records = mapper.selectList(new LambdaQueryWrapper<ExtensionPairing>()
        .eq(ExtensionPairing::getStatus, status)
        .orderByDesc(ExtensionPairing::getId));
    Set<String> seen = new HashSet<>();
    List<PairingSummary> result = new ArrayList<>();
    for (ExtensionPairing record : records) {
      if (!seen.add(record.getExtensionId())) continue;
      result.add(new PairingSummary(record.getId(), record.getExtensionId(), record.getDisplayName(), record.getStatus(), record.getCreatedTime(), record.getApprovedTime()));
    }
    return result;
  }
  private void revokeClaimed(String extensionId) {
    mapper.update(null, new LambdaUpdateWrapper<ExtensionPairing>()
        .eq(ExtensionPairing::getExtensionId, extensionId)
        .eq(ExtensionPairing::getStatus, "CLAIMED")
        .set(ExtensionPairing::getStatus, "REVOKED")
        .set(ExtensionPairing::getTokenHash, null)
        .set(ExtensionPairing::getClaimSecretHash, null));
  }
  private String token() { byte[] b = new byte[32]; new SecureRandom().nextBytes(b); return Base64.getUrlEncoder().withoutPadding().encodeToString(b); }
  private String hash(String value) { try { byte[] d = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)); StringBuilder s=new StringBuilder(); for(byte b:d)s.append(String.format("%02x",b)); return s.toString(); } catch(Exception e){ throw new IllegalStateException(e); } }
}
