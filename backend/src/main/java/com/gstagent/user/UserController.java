package com.gstagent.user;

import com.gstagent.auth.AuthUser;
import com.gstagent.business.BusinessRepository;
import com.gstagent.common.ApiException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

/** Team management for a business. Admin only. */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class UserController {

    private final UserRepository users;
    private final BusinessRepository businesses;
    private final PasswordEncoder passwordEncoder;

    public record UserDto(Long id, String fullName, String email, Role role, boolean active, Instant createdAt) {
        static UserDto of(User u) {
            return new UserDto(u.getId(), u.getFullName(), u.getEmail(), u.getRole(), u.isActive(), u.getCreatedAt());
        }
    }

    public record CreateUserRequest(@NotBlank @Size(max = 120) String fullName, @NotBlank @Email String email,
                                    @NotBlank @Size(min = 8, max = 72) String password, @NotNull Role role) {
    }

    public record UpdateUserRequest(@NotNull Role role, boolean active) {
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<UserDto> list(@AuthenticationPrincipal AuthUser me) {
        return users.findByBusinessIdOrderByCreatedAtAsc(me.businessId()).stream().map(UserDto::of).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public UserDto create(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody CreateUserRequest req) {
        if (users.existsByEmailIgnoreCase(req.email())) {
            throw ApiException.conflict("An account with this email already exists");
        }
        User u = new User();
        u.setBusiness(businesses.getReferenceById(me.businessId()));
        u.setFullName(req.fullName().trim());
        u.setEmail(req.email().toLowerCase());
        u.setPasswordHash(passwordEncoder.encode(req.password()));
        u.setRole(req.role());
        return UserDto.of(users.save(u));
    }

    @PutMapping("/{id}")
    @Transactional
    public UserDto update(@AuthenticationPrincipal AuthUser me, @PathVariable Long id,
                          @Valid @RequestBody UpdateUserRequest req) {
        User u = users.findByIdAndBusinessId(id, me.businessId()).orElseThrow(() -> ApiException.notFound("User"));
        if (u.getId().equals(me.userId()) && (req.role() != Role.ADMIN || !req.active())) {
            throw ApiException.badRequest("You cannot demote or deactivate yourself");
        }
        u.setRole(req.role());
        u.setActive(req.active());
        return UserDto.of(u);
    }
}
