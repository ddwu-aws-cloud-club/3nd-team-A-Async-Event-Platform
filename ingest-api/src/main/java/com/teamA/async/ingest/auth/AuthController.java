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

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest req) {
        String userId = req.getUserId();
        // ✅ G0: user-XXXX 형태면 허용 (나중에 DB/회원가입으로 교체)
        if (userId == null || !userId.startsWith("user-")) {
            return ResponseEntity.status(401).body(Map.of("message", "invalid user"));
        }

        String token = jwtProvider.issue(userId);
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

