package com.jobtracker.applymate.security;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Service @RequiredArgsConstructor
public class ExtensionTokenService {
  private final ExtensionPairingMapper mapper;
  public void require(String token) {
    if (token == null || token.isBlank()) throw new IllegalArgumentException("EXTENSION_TOKEN_REQUIRED");
    boolean valid = mapper.selectCount(new LambdaQueryWrapper<ExtensionPairing>()
        .eq(ExtensionPairing::getStatus, "CLAIMED")
        .eq(ExtensionPairing::getTokenHash, hash(token))) > 0;
    if (!valid) throw new IllegalArgumentException("EXTENSION_TOKEN_INVALID");
  }
  private String hash(String value) { try { byte[] d=MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)); StringBuilder s=new StringBuilder(); for(byte b:d)s.append(String.format("%02x",b)); return s.toString(); } catch(Exception e){throw new IllegalStateException(e);} }
}
