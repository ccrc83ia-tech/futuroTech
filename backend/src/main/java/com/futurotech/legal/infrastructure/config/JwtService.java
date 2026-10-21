package com.futurotech.legal.infrastructure.config;

import com.futurotech.legal.domain.model.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Emisión y validación de JWT (access + refresh) firmados con HMAC-SHA256.
 * El secreto es obligatorio: si falta, la aplicación no arranca.
 */
@Service
public class JwtService {

    private static final String CLAIM_ROLE = "role";
    private static final String CLAIM_PERMISSIONS = "perms";
    private static final String CLAIM_TYPE = "typ";
    private static final String TYPE_ACCESS = "access";
    private static final String TYPE_REFRESH = "refresh";

    private final String secret;
    private final long ttlSeconds;
    private final long refreshTtlSeconds;
    private SecretKey key;

    public JwtService(
            @Value("${security.jwt.secret:}") String secret,
            @Value("${security.jwt.ttl-seconds:28800}") long ttlSeconds,
            @Value("${security.jwt.refresh-ttl-seconds:604800}") long refreshTtlSeconds) {
        this.secret = secret;
        this.ttlSeconds = ttlSeconds;
        this.refreshTtlSeconds = refreshTtlSeconds;
    }

    @PostConstruct
    void init() {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "security.jwt.secret (env JWT_SECRET) es obligatorio. "
                    + "Genere una clave aleatoria larga, p.ej. 'openssl rand -base64 48'.");
        }
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalStateException("JWT_SECRET debe tener al menos 32 bytes.");
        }
        this.key = Keys.hmacShaKeyFor(bytes);
    }

    public String generateAccessToken(User user) {
        List<String> perms = user.role().defaultPermissions().stream()
                .map(p -> p.value())
                .toList();
        List<String> effective = user.customPermissions() != null ? user.customPermissions() : perms;
        return build(user.userId(), user.role().value(), effective, TYPE_ACCESS, ttlSeconds);
    }

    public String generateRefreshToken(User user) {
        return build(user.userId(), user.role().value(), List.of(), TYPE_REFRESH, refreshTtlSeconds);
    }

    private String build(String subject, String role, List<String> perms, String type, long seconds) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .subject(subject)
                .claim(CLAIM_ROLE, role)
                .claim(CLAIM_PERMISSIONS, perms)
                .claim(CLAIM_TYPE, type)
                .issuedAt(new Date(now))
                .expiration(new Date(now + seconds * 1000))
                .signWith(key)
                .compact();
    }

    public Claims parse(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }

    public boolean isRefreshToken(Claims claims) {
        return TYPE_REFRESH.equals(claims.get(CLAIM_TYPE, String.class));
    }

    @SuppressWarnings("unchecked")
    public List<String> permissions(Claims claims) {
        Object raw = claims.get(CLAIM_PERMISSIONS);
        return raw instanceof List ? (List<String>) raw : List.of();
    }

    public String role(Claims claims) {
        return claims.get(CLAIM_ROLE, String.class);
    }
}
