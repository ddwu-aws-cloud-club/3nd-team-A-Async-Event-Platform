package com.teamA.async.ingest.auth;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Set;

@RestController
@RequiredArgsConstructor
@RequestMapping("/auth")
public class AuthController {

    private final JwtProvider jwtProvider;

    // ✅ G0: 테스트 유저만 허용 (나중에 DB/회원가입으로 교체)
    private static final Set<String> ALLOWED = Set.of(
            "user-001", "user-002", "user-003", "user-004"
    );

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest req) {
        if (!ALLOWED.contains(req.getUserId())) {
            return ResponseEntity.status(401).body(Map.of("message", "invalid user"));
        }
        String token = jwtProvider.issue(req.getUserId());
        return ResponseEntity.ok(Map.of(
                "accessToken", token,
                "tokenType", "Bearer"
        ));
    }

    @Data
    public static class LoginRequest {
        @NotBlank
        private String userId;
    }
}

