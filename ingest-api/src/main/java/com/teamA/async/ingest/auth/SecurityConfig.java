package com.teamA.async.ingest.auth;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // CSRF 비활성화
                .csrf(csrf -> csrf.disable())

                // 세션 미사용 (stateless)
                .sessionManagement(sm ->
                        sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )

                // ✅ 모든 요청 인증 없이 허용
                .authorizeHttpRequests(auth ->
                        auth.anyRequest().permitAll()
                )

                // ❌ JwtAuthFilter 절대 추가하지 않음
                // .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)

                // 기본 설정 허용
                .httpBasic(Customizer.withDefaults());

        return http.build();
    }
}
