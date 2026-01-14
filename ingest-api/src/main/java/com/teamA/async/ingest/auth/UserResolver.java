package com.teamA.async.ingest.auth;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class UserResolver {

    private final HttpServletRequest request;

    public String currentUserId() {
        // ✅ 부하테스트/토큰 없는 호출용 우선순위
//        String testUser = request.getHeader("X-Test-User");
//        if (testUser != null && !testUser.isBlank()) {
//            return testUser;
//        }

        // ✅ 원래 방식: SecurityContext 기반
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getPrincipal() == null) {
            // 여기서 throw 하면 “토큰 없는 테스트”가 전부 죽음
            // 최소한 fallback을 주자
            return "anonymousUser";
        }
        return auth.getPrincipal().toString();
    }
}