package com.teamA.async.ingest.auth;

import org.springframework.stereotype.Component;

/**
 * G0 단계용 임시 UserResolver
 *
 * ❗주의
 * - 실제 인증/인가 로직 아님
 * - Step1에서는 "userId 공급자" 역할만 수행
 * - G1에서 JWT / SecurityContext 기반 구현으로 교체 예정
 */
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class UserResolver {

    public String currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getPrincipal() == null) {
            throw new IllegalStateException("No authenticated user");
        }
        return auth.getPrincipal().toString(); // principal=userId
    }
}
