package com.x.user.controller;

import com.x.user.dto.UserAuthResponse;
import com.x.user.dto.UserRegistrationRequest;
import com.x.user.dto.UserResponse;
import com.x.user.dto.StoreAccessResponse;
import com.x.user.service.UserAuthenticationLookupService;
import com.x.user.repository.StoreMemberRepository;
import com.x.user.repository.RoleRepository;
import com.x.user.model.User;
import com.x.user.repository.UserRepository;
import com.sharedlib.response.ApiResponse;
import com.sharedlib.response.PageResponse;
import com.x.redis.cache.CacheNames;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;

import java.util.Set;

@RestController
@RequestMapping("/internal/users")
@RequiredArgsConstructor
@Validated
public class UserController {

    private final UserRepository userRepository;
    private final StoreMemberRepository storeMemberRepository;
    private final RoleRepository roleRepository;
    private final com.x.user.repository.RefreshTokenRepository refreshTokenRepository;
    private final UserAuthenticationLookupService userAuthenticationLookupService;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @PostMapping
    @Transactional
    public ResponseEntity<ApiResponse<UserResponse>> register(
            @jakarta.validation.Valid @RequestBody UserRegistrationRequest request) {
        String username = request.username().trim();
        if (userRepository.findByUsername(username).isPresent()) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(ApiResponse.error(HttpStatus.CONFLICT.value(), "Username is already in use"));
        }

        User user = userRepository.save(User.builder()
                .username(username)
                .password(passwordEncoder.encode(request.password()))
                .fullName(request.fullName().trim())
                .email(request.email() == null ? null : request.email().trim())
                .phone(request.phone() == null ? null : request.phone().trim())
                .failedAttempt(0)
                .isLocked(false)
                .status(1)
                .build());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(HttpStatus.CREATED.value(), "Account created", UserResponse.from(user)));
    }

    /** Removes an account created by a registration workflow that later failed. */
    @CacheEvict(cacheNames = CacheNames.USER_BY_USERNAME, key = "#username")
    @DeleteMapping("/{username}/registration-failure")
    @Transactional
    public ResponseEntity<Void> deleteFailedRegistration(@PathVariable String username) {
        userRepository.findByUsername(username.trim()).ifPresent(user -> {
            refreshTokenRepository.deleteByUser(user);
            storeMemberRepository.deleteAll(storeMemberRepository.findByUser(user));
            userRepository.delete(user);
        });
        return ResponseEntity.noContent().build();
    }

    /** Assigns the workspace creator the OWNER role for its newly created default store. */
    @PostMapping("/{userId}/stores/{storeId}/owner")
    @Transactional
    public ResponseEntity<Void> assignOwnerStoreMembership(
            @PathVariable @Positive Long userId,
            @PathVariable @Positive Long storeId) {
        if (!storeMemberRepository.existsByUserIdAndStoreId(userId, storeId)) {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
            var ownerRole = roleRepository.findByRoleCode("OWNER")
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "OWNER role is not configured"));
            storeMemberRepository.save(com.x.user.model.StoreMember.builder()
                    .user(user)
                    .storeId(storeId)
                    .role(ownerRole)
                    .build());
        }
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    @Transactional(readOnly = true)
    public ResponseEntity<ApiResponse<PageResponse<UserResponse>>> getAllUsers(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        var users = userRepository.findAll(PageRequest.of(page, size, Sort.by("id").ascending()));
        var responses = users.getContent().stream().map(UserResponse::from).toList();
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), new PageResponse<>(
                responses, users.getNumber(), users.getSize(), users.getTotalElements(),
                users.getTotalPages(), users.hasNext())));
    }

    /**
     * Auth lookup for BFF.
     * Uses 2 DB queries total (user + permission codes) instead of N+1 lazy loads.
     */
    @GetMapping("/{username}")
    public ResponseEntity<ApiResponse<UserAuthResponse>> getUserByUsername(@PathVariable String username) {
        return java.util.Optional.ofNullable(userAuthenticationLookupService.findByUsername(username))
                .map(response -> ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), response)))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.error(HttpStatus.NOT_FOUND.value(), "User not found")));
    }

    @GetMapping("/{username}/stores/{storeId}/access")
    @Transactional(readOnly = true)
    public ResponseEntity<ApiResponse<StoreAccessResponse>> hasStoreAccess(
            @PathVariable String username, @PathVariable @Positive Long storeId) {
        boolean allowed = storeMemberRepository.existsByUserUsernameAndStoreId(username, storeId);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(),
                new StoreAccessResponse(storeId, allowed)));
    }
}
