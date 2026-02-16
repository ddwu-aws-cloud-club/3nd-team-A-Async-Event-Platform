package com.teamA.async.admin.auth;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;

@Component
public class JwtProvider {

    private final Key key;
    private final long ttlMillis;

    public JwtProvider(
            @Value("${security.jwt.secret}") String secret,
            @Value("${security.jwt.ttl-seconds}") long ttlSeconds
    ) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.ttlMillis= ttlSeconds * 1000L;
    }

    public String issue(String userId, java.util.List<String> roles) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .subject(userId)               // ✅ userId는 sub에
                .claim("roles", roles)
                .issuedAt(new Date(now))
                .expiration(new Date(now + ttlMillis))
                .signWith(key)
                .compact();
    }

    public String parseUserId(String token) {
        JwtParser parser = Jwts.parser().verifyWith((javax.crypto.SecretKey) key).build();
        Jws<Claims> jws = parser.parseSignedClaims(token);
        return jws.getPayload().getSubject();
    }

    public java.util.List<String> parseRoles(String token) {
        JwtParser parser = Jwts.parser().verifyWith((javax.crypto.SecretKey) key).build();
        Jws<Claims> jws = parser.parseSignedClaims(token);

        Object raw = jws.getPayload().get("roles");
        if (raw == null) return java.util.List.of();

        if (raw instanceof java.util.List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        return java.util.List.of(String.valueOf(raw));
    }

}

