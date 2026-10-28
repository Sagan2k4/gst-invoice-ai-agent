package com.gstagent.auth;

import com.gstagent.auth.AuthDtos.*;
import com.gstagent.business.Business;
import com.gstagent.business.BusinessRepository;
import com.gstagent.common.ApiException;
import com.gstagent.user.Role;
import com.gstagent.user.User;
import com.gstagent.user.UserRepository;
import com.gstagent.validation.GstinValidator;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository users;
    private final BusinessRepository businesses;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final TokenStore tokenStore;

    static String refreshKey(String jti) {
        return "refresh:" + jti;
    }

    static String blacklistKey(String jti) {
        return "blacklist:" + jti;
    }

    @Transactional
    public TokenResponse register(RegisterRequest req) {
        if (users.existsByEmailIgnoreCase(req.email())) {
            throw ApiException.conflict("An account with this email already exists");
        }
        Business business = new Business();
        business.setName(req.businessName().trim());
        if (StringUtils.hasText(req.businessGstin())) {
            if (!GstinValidator.isValid(req.businessGstin())) {
                throw ApiException.badRequest("businessGstin: checksum digit does not match");
            }
            business.setGstin(req.businessGstin());
            business.setStateCode(req.businessGstin().substring(0, 2));
        }
        businesses.save(business);

        User user = new User();
        user.setBusiness(business);
        user.setFullName(req.fullName().trim());
        user.setEmail(req.email().toLowerCase());
        user.setPasswordHash(passwordEncoder.encode(req.password()));
        user.setRole(Role.ADMIN);
        users.save(user);
        return issueTokens(user);
    }

    @Transactional(readOnly = true)
    public TokenResponse login(LoginRequest req) {
        User user = users.findByEmailIgnoreCase(req.email())
                .filter(u -> passwordEncoder.matches(req.password(), u.getPasswordHash()))
                .orElseThrow(() -> ApiException.unauthorized("Invalid email or password"));
        if (!user.isActive()) {
            throw ApiException.unauthorized("This account has been deactivated");
        }
        return issueTokens(user);
    }

    /** Rotates the refresh token: the presented one is revoked and a fresh pair is issued. */
    @Transactional(readOnly = true)
    public TokenResponse refresh(RefreshRequest req) {
        Claims claims = parseOrUnauthorized(req.refreshToken());
        if (!JwtService.TYPE_REFRESH.equals(claims.get("typ", String.class))
                || !tokenStore.exists(refreshKey(claims.getId()))) {
            throw ApiException.unauthorized("Refresh token is invalid or has been revoked");
        }
        tokenStore.delete(refreshKey(claims.getId()));
        User user = users.findById(Long.valueOf(claims.getSubject()))
                .filter(User::isActive)
                .orElseThrow(() -> ApiException.unauthorized("Account is no longer active"));
        return issueTokens(user);
    }

    public void logout(String accessToken, String refreshToken) {
        if (accessToken != null) {
            try {
                Claims claims = jwtService.parse(accessToken);
                Duration remaining = Duration.between(Instant.now(), claims.getExpiration().toInstant());
                if (!remaining.isNegative()) {
                    tokenStore.put(blacklistKey(claims.getId()), "1", remaining);
                }
            } catch (JwtException | IllegalArgumentException ignored) {
                // already unusable
            }
        }
        if (refreshToken != null) {
            try {
                tokenStore.delete(refreshKey(jwtService.parse(refreshToken).getId()));
            } catch (JwtException | IllegalArgumentException ignored) {
                // already unusable
            }
        }
    }

    @Transactional(readOnly = true)
    public UserInfo me(AuthUser authUser) {
        return users.findById(authUser.userId()).map(AuthService::toInfo)
                .orElseThrow(() -> ApiException.notFound("User"));
    }

    private TokenResponse issueTokens(User user) {
        String access = jwtService.issueAccessToken(user);
        String refresh = jwtService.issueRefreshToken(user);
        tokenStore.put(refreshKey(jwtService.parse(refresh).getId()), user.getId().toString(), jwtService.refreshTtl());
        return new TokenResponse(access, refresh, jwtService.accessTtl().toSeconds(), toInfo(user));
    }

    private Claims parseOrUnauthorized(String token) {
        try {
            return jwtService.parse(token);
        } catch (JwtException | IllegalArgumentException e) {
            throw ApiException.unauthorized("Refresh token is invalid or expired");
        }
    }

    static UserInfo toInfo(User u) {
        Business b = u.getBusiness();
        return new UserInfo(u.getId(), u.getFullName(), u.getEmail(), u.getRole(), b.getId(), b.getName(), b.getGstin());
    }
}
