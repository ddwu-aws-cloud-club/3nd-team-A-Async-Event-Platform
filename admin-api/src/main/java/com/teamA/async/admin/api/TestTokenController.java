package com.teamA.async.admin.api;

import com.teamA.async.admin.auth.JwtProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class TestTokenController {

    private final JwtProvider jwtProvider;

    @GetMapping("/test/admin-token")
    public String issueAdminToken() {
        return jwtProvider.issue(
                "test-admin",
                List.of("ROLE_ADMIN")
        );
    }
}
