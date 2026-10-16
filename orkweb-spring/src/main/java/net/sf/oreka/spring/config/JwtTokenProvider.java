package net.sf.oreka.spring.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 工具类 —— 生成/验证 token
 */
@Component
public class JwtTokenProvider {

    private final SecretKey key;
    private final long accessTokenTtlMs;
    private final long refreshTokenTtlMs;

    public JwtTokenProvider(
            @Value("${orkweb.jwt.secret}") String secret,
            @Value("${orkweb.jwt.access-token-ttl-minutes}") long accessTtlMinutes,
            @Value("${orkweb.jwt.refresh-token-ttl-days}") long refreshTtlDays) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenTtlMs = accessTtlMinutes * 60_000;
        this.refreshTokenTtlMs = refreshTtlDays * 86_400_000;
    }

    public String generateAccessToken(int userId, String loginString) {
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("loginString", loginString)
                .claim("type", "access")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + accessTokenTtlMs))
                .signWith(key)
                .compact();
    }

    public String generateRefreshToken(int userId) {
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("type", "refresh")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + refreshTokenTtlMs))
                .signWith(key)
                .compact();
    }

    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public boolean isValid(String token) {
        try {
            parse(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public int getUserId(Claims claims) {
        return Integer.parseInt(claims.getSubject());
    }

    public String getLoginString(Claims claims) {
        return claims.get("loginString", String.class);
    }
}
