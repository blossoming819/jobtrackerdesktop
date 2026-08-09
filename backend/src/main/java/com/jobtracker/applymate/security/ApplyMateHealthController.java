package com.jobtracker.applymate.security;
import com.jobtracker.common.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.Map;
@RestController @RequestMapping("/api/applymate/v1")
public class ApplyMateHealthController { @GetMapping("/health") public Result<Map<String,Object>> health(){ return Result.ok(Map.of("status","UP","pairingRequired",true)); } }
