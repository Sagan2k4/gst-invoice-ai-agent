package com.gstagent.auth;

import com.gstagent.user.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class AuthDtos {

    private AuthDtos() {
    }

    /** Sign-up creates a business and its first (ADMIN) user. */
    public record RegisterRequest(
            @NotBlank @Size(max = 200) String businessName,
            @Pattern(regexp = "^$|^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z][1-9A-Z]Z[0-9A-Z]$", message = "is not a valid GSTIN format")
            String businessGstin,
            @NotBlank @Size(max = 120) String fullName,
            @NotBlank @Email String email,
            @NotBlank @Size(min = 8, max = 72) String password) {
    }

    public record LoginRequest(@NotBlank @Email String email, @NotBlank String password) {
    }

    public record RefreshRequest(@NotBlank String refreshToken) {
    }

    public record TokenResponse(String accessToken, String refreshToken, long expiresInSeconds, UserInfo user) {
    }

    public record UserInfo(Long id, String fullName, String email, Role role, Long businessId, String businessName,
                           String businessGstin) {
    }
}
