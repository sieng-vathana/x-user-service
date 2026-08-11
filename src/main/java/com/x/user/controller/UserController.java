package com.x.user.controller;

import com.x.user.dto.UserAuthResponse;
import com.x.user.dto.OwnerAccessRequest;
import com.x.user.dto.PermissionResponse;
import com.x.user.dto.RoleUpsertRequest;
import com.x.user.dto.StaffUserCreateRequest;
import com.x.user.dto.UserRegistrationRequest;
import com.x.user.dto.UserResponse;
import com.x.user.dto.StoreAccessResponse;
import com.x.user.dto.RoleDetailsResponse;
import com.x.user.service.RoleManagementService;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
    private final RoleManagementService roleManagementService;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @CacheEvict(cacheNames = CacheNames.USER_BY_USERNAME, key = "#request.username().trim()")
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
    @CacheEvict(cacheNames = CacheNames.USER_BY_USERNAME, key = "#username.trim()")
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
    @CacheEvict(cacheNames = CacheNames.USER_BY_USERNAME, allEntries = true)
    @PostMapping("/{userId}/stores/{storeId}/owner")
    @Transactional
    public ResponseEntity<Void> assignOwnerStoreMembership(
            @PathVariable @Positive Long userId,
            @PathVariable @Positive Long storeId) {
        roleManagementService.ensureOwnerAccess(userId, 1L, List.of(storeId));
        return ResponseEntity.noContent().build();
    }

    @CacheEvict(cacheNames = CacheNames.USER_BY_USERNAME, allEntries = true)
    @PostMapping("/{userId}/businesses/{businessId}/owner-access")
    @Transactional
    public ResponseEntity<Void> ensureOwnerBusinessAccess(
            @PathVariable @Positive Long userId,
            @PathVariable @Positive Long businessId,
            @jakarta.validation.Valid @RequestBody OwnerAccessRequest request) {
        roleManagementService.ensureOwnerAccess(userId, businessId, request.storeIds());
        return ResponseEntity.noContent().build();
    }

    @CacheEvict(cacheNames = CacheNames.USER_BY_USERNAME, allEntries = true)
    @PostMapping("/staff")
    @Transactional
    public ResponseEntity<ApiResponse<UserResponse>> createStaffUser(
            @jakarta.validation.Valid @RequestBody StaffUserCreateRequest request) {
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
                .status(request.status() == null ? 1 : request.status())
                .build());
        roleManagementService.assignStaffRole(
                user, request.businessId(), request.roleId(), request.storeIds());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        HttpStatus.CREATED.value(),
                        "Staff account created",
                        UserResponse.from(user, storeMemberRepository.findByUserAndRoleBusinessId(
                                user, request.businessId()))));
    }

    @GetMapping({"", "/"})
    @Transactional(readOnly = true)
    public ResponseEntity<ApiResponse<PageResponse<UserResponse>>> getAllUsers(
            @RequestParam(required = false) @Positive Long businessId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        var pageable = businessId == null
                ? PageRequest.of(page, size, Sort.by("id").ascending())
                : PageRequest.of(page, size);
        var users = businessId == null
                ? userRepository.findAll(pageable)
                : userRepository.findAllByBusinessId(businessId, pageable);
        List<User> userList = users.getContent();
        Map<Long, List<com.x.user.model.StoreMember>> membershipsByUser = userList.isEmpty()
                ? Map.of()
                : (businessId == null
                        ? storeMemberRepository.findByUserIn(userList)
                        : storeMemberRepository.findByUserInAndRoleBusinessId(userList, businessId)).stream()
                        .collect(Collectors.groupingBy(member -> member.getUser().getId()));
        var responses = userList.stream()
                .map(user -> UserResponse.from(user,
                        membershipsByUser.getOrDefault(user.getId(), List.of())))
                .toList();
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), new PageResponse<>(
                responses, users.getNumber(), users.getSize(), users.getTotalElements(),
                users.getTotalPages(), users.hasNext())));
    }

    /**
     * Auth lookup for BFF.
     * Uses 2 DB queries total (user + permission codes) instead of N+1 lazy loads.
     */
    @GetMapping("/{username}")
    public ResponseEntity<ApiResponse<UserAuthResponse>> getUserByUsername(
            @PathVariable String username,
            @RequestParam(required = false) @Positive Long businessId) {
        return java.util.Optional.ofNullable(userAuthenticationLookupService.findByUsername(username, businessId))
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

    @GetMapping("/by-id/{id:[0-9]+}")
    @Transactional(readOnly = true)
    public ResponseEntity<ApiResponse<UserResponse>> getUserById(
            @PathVariable Long id,
            @RequestParam(required = false) @Positive Long businessId) {
        return userRepository.findById(id)
                .map(user -> Map.entry(user, businessId == null
                        ? storeMemberRepository.findByUser(user)
                        : storeMemberRepository.findByUserAndRoleBusinessId(user, businessId)))
                .filter(entry -> businessId == null || !entry.getValue().isEmpty())
                .map(entry -> ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(),
                        UserResponse.from(entry.getKey(), entry.getValue()))))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.error(HttpStatus.NOT_FOUND.value(), "User not found")));
    }

    @CacheEvict(cacheNames = CacheNames.USER_BY_USERNAME, allEntries = true)
    @PutMapping("/{id:[0-9]+}")
    @Transactional
    public ResponseEntity<ApiResponse<UserResponse>> updateUser(
            @PathVariable Long id,
            @jakarta.validation.Valid @RequestBody com.x.user.dto.UserUpdateRequest request) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        if (request.businessId() != null) {
            List<com.x.user.model.StoreMember> businessMemberships =
                    storeMemberRepository.findByUserAndRoleBusinessId(user, request.businessId());
            if (businessMemberships.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found for this business");
            }
            boolean isBusinessOwner = businessMemberships.stream()
                    .map(com.x.user.model.StoreMember::getRole)
                    .filter(java.util.Objects::nonNull)
                    .anyMatch(role -> "OWNER".equalsIgnoreCase(role.getRoleCode()));
            if (isBusinessOwner) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Business owner cannot be changed from staff management");
            }
        }
        if (request.fullName() != null && !request.fullName().isBlank()) {
            user.setFullName(request.fullName().trim());
        }
        if (request.email() != null) {
            user.setEmail(request.email().trim());
        }
        if (request.phone() != null) {
            user.setPhone(request.phone().trim());
        }
        if (request.status() != null) {
            user.setStatus(request.status());
        }
        if (request.password() != null && !request.password().isBlank()) {
            user.setPassword(passwordEncoder.encode(request.password()));
        }
        User updated = userRepository.save(user);
        if (request.businessId() != null || request.roleId() != null || request.storeIds() != null) {
            if (request.businessId() == null || request.roleId() == null || request.storeIds() == null) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "businessId, roleId, and storeIds are required when changing a staff role");
            }
            roleManagementService.assignStaffRole(
                    updated, request.businessId(), request.roleId(), request.storeIds());
        }
        List<com.x.user.model.StoreMember> memberships = request.businessId() == null
                ? storeMemberRepository.findByUser(updated)
                : storeMemberRepository.findByUserAndRoleBusinessId(updated, request.businessId());
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), "User updated successfully",
                UserResponse.from(updated, memberships)));
    }

    @CacheEvict(cacheNames = CacheNames.USER_BY_USERNAME, allEntries = true)
    @DeleteMapping("/{id:[0-9]+}")
    @Transactional
    public ResponseEntity<Void> deleteUserById(
            @PathVariable Long id,
            @RequestParam(required = false) @Positive Long businessId) {
        userRepository.findById(id).ifPresent(user -> {
            List<com.x.user.model.StoreMember> memberships = businessId == null
                    ? storeMemberRepository.findByUser(user)
                    : storeMemberRepository.findByUserAndRoleBusinessId(user, businessId);
            if (businessId != null && memberships.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found for this business");
            }
            boolean ownsBusiness = memberships.stream()
                    .map(com.x.user.model.StoreMember::getRole)
                    .filter(java.util.Objects::nonNull)
                    .anyMatch(role -> "OWNER".equalsIgnoreCase(role.getRoleCode()));
            if (ownsBusiness) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Business owner cannot be deleted");
            }
            storeMemberRepository.deleteAll(memberships);
            if (storeMemberRepository.findByUser(user).isEmpty()) {
                refreshTokenRepository.deleteByUser(user);
                userRepository.delete(user);
            }
        });
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/roles")
    @Transactional(readOnly = true)
    public ResponseEntity<ApiResponse<?>> getRoles(
            @RequestParam(required = false) @Positive Long businessId) {
        Object roles = businessId == null
                ? roleRepository.findAll()
                : roleManagementService.listRoles(businessId);
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), roles));
    }

    @GetMapping("/roles/{id:[0-9]+}")
    @Transactional(readOnly = true)
    public ResponseEntity<ApiResponse<RoleDetailsResponse>> getRoleDetails(
            @PathVariable Long id,
            @RequestParam(required = false) @Positive Long businessId) {
        return roleRepository.findById(id)
                .filter(role -> businessId == null || businessId.equals(role.getBusinessId()))
                .map(role -> ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(),
                        roleManagementService.getRole(role.getBusinessId(), role.getId()))))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.error(HttpStatus.NOT_FOUND.value(), "Role not found")));
    }

    @GetMapping("/roles/permissions")
    @Transactional(readOnly = true)
    public ResponseEntity<ApiResponse<List<PermissionResponse>>> getPermissions() {
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), roleManagementService.listPermissions()));
    }

    @PostMapping("/roles")
    public ResponseEntity<ApiResponse<RoleDetailsResponse>> createRole(
            @jakarta.validation.Valid @RequestBody RoleUpsertRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        HttpStatus.CREATED.value(),
                        "Role created",
                        roleManagementService.create(request)));
    }

    @PutMapping("/roles/{id:[0-9]+}")
    public ResponseEntity<ApiResponse<RoleDetailsResponse>> updateRole(
            @PathVariable Long id,
            @jakarta.validation.Valid @RequestBody RoleUpsertRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                HttpStatus.OK.value(),
                "Role updated",
                roleManagementService.update(id, request)));
    }

    @DeleteMapping("/roles/{id:[0-9]+}")
    public ResponseEntity<Void> deleteRole(
            @PathVariable Long id,
            @RequestParam @Positive Long businessId) {
        roleManagementService.delete(businessId, id);
        return ResponseEntity.noContent().build();
    }
}
