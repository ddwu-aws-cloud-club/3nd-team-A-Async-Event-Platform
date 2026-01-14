package com.teamA.async.ingest.auth;

import com.teamA.async.common.domain.model.UserItem; // 임포트 필수!
import com.teamA.async.ingest.ddb.UserRepository;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("api/auth")
public class AuthController {
    private final JwtProvider jwtProvider;
    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder;

    @PostMapping("/signup")
    public ResponseEntity<?> signup(@RequestBody LoginRequest req) {
        if (userRepository.findByUserId(req.getUserId()) != null) {
            return ResponseEntity.badRequest().body(Map.of("message", "Already exists"));
        }

        String assignedRole = req.getUserId().startsWith("admin") ? "ROLE_ADMIN" : "ROLE_USER";

        UserItem user = UserItem.builder()
                .userId(req.getUserId())
                .password(passwordEncoder.encode(req.getPassword()))
                .role(assignedRole)
                .createdAt(System.currentTimeMillis())
                .build();

        userRepository.save(user);
        return ResponseEntity.ok(Map.of("message", "Success"));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest req) {
        UserItem user = userRepository.findByUserId(req.getUserId());

        if (user == null || !passwordEncoder.matches(req.getPassword(), user.getPassword())) {
            return ResponseEntity.status(401).body(Map.of("message", "Invalid credentials"));
        }

        String token = jwtProvider.issue(user.getUserId(), java.util.List.of(user.getRole()));

        return ResponseEntity.ok(Map.of(
                "accessToken", token,
                "tokenType", "Bearer"
        ));
    }

    @Data
    public static class LoginRequest {
        @NotBlank
        private String userId;
        @NotBlank
        private String password;
    }
}