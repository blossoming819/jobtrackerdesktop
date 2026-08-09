package com.jobtracker.applymate.security;
import com.jobtracker.common.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.Map;
@RestController @RequiredArgsConstructor @RequestMapping("/api/applymate/v1")
public class ApplyMateHealthController {
  private final Environment environment;
  @GetMapping("/health") public Result<Map<String,Object>> health(){
    boolean desktop = java.util.Arrays.asList(environment.getActiveProfiles()).contains("desktop");
    return Result.ok(Map.of("status","UP", "pairingRequired",true,
        "runtime", desktop ? "DESKTOP" : "WEB",
        "dataStore", desktop ? "H2" : "MYSQL",
        "dataScope", desktop ? "桌面端本地 H2 数据库" : "Web 端 MySQL 数据库"));
  }
}
