package com.gstagent.auth;

import com.gstagent.config.AppProperties;
import com.gstagent.user.Role;
import com.gstagent.user.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Service
public class JwtService {

    static final String TYPE_ACCESS = "access";
    static final String TYPE_REFRESH = "refresh";

    private final SecretKey key;
    private final Duration accessTtl;
    private final Duration refreshTtl;

    public JwtService(AppProperties props) {
        this.key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(props.jwt().secret()));
        this.accessTtl = props.jwt().accessTokenTtl();
        this.refreshTtl = props.jwt().refreshTokenTtl();
    }

    public String issueAccessToken(User user) {
        return build(user, TYPE_ACCESS, accessTtl);
    }

    public String issueRefreshToken(User user) {
        return build(user, TYPE_REFRESH, refreshTtl);
    }

    public Duration accessTtl() {
        return accessTtl;
    }

    public Duration refreshTtl() {
        return refreshTtl;
    }

    /** Verifies signature and expiry; throws {@link JwtException} when the token is not usable. */
    public Claims parse(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }

    public AuthUser toAuthUser(Claims claims) {
        return new AuthUser(
                Long.valueOf(claims.getSubject()),
                claims.get("bid", Long.class),
                claims.get("email", String.class),
                Role.valueOf(claims.get("role", String.class)));
    }

    private String build(User user, String type, Duration ttl) {
        Instant now = Instant.now();
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(user.getId().toString())
                .claim("bid", user.getBusiness().getId())
                .claim("email", user.getEmail())
                .claim("role", user.getRole().name())
                .claim("typ", type)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttl)))
                .signWith(key)
                .compact();
    }
}
