package com.x.user.service;

import com.x.redis.cache.CacheNames;
import com.x.user.dto.PermissionResponse;
import com.x.user.dto.RoleDetailsResponse;
import com.x.user.dto.RoleSummaryResponse;
import com.x.user.dto.RoleUpsertRequest;
import com.x.user.model.Permission;
import com.x.user.model.Role;
import com.x.user.model.RolePermission;
import com.x.user.model.StoreMember;
import com.x.user.model.User;
import com.x.user.repository.PermissionRepository;
import com.x.user.repository.RolePermissionRepository;
import com.x.user.repository.RoleRepository;
import com.x.user.repository.StoreMemberRepository;
import com.x.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RoleManagementService {

    private static final String OWNER_CODE = "OWNER";

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final StoreMemberRepository storeMemberRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<RoleSummaryResponse> listRoles(Long businessId) {
        List<Role> roles = roleRepository.findAllByBusinessIdOrderByIsSystemDescRoleNameAsc(businessId);
        Map<Long, Long> permissionCounts = roles.isEmpty()
                ? Map.of()
                : rolePermissionRepository.findByRoleIn(roles).stream()
                        .filter(link -> link.getRole() != null && link.getRole().getId() != null)
                        .collect(Collectors.groupingBy(link -> link.getRole().getId(), Collectors.counting()));
        long allPermissionCount = permissionRepository.count();
        return roles.stream()
                .map(role -> RoleSummaryResponse.from(
                        role,
                        isOwner(role)
                                ? allPermissionCount
                                : permissionCounts.getOrDefault(role.getId(), 0L)))
                .toList();
    }

    @Transactional(readOnly = true)
    public RoleDetailsResponse getRole(Long businessId, Long roleId) {
        Role role = requireRole(businessId, roleId);
        List<Permission> allPermissions = sortedPermissions();
        List<RolePermission> assigned = isOwner(role)
                ? allPermissions.stream()
                        .map(permission -> RolePermission.builder().role(role).permission(permission).build())
                        .toList()
                : rolePermissionRepository.findByRole(role);
        return RoleDetailsResponse.from(role, allPermissions, assigned);
    }

    @Transactional(readOnly = true)
    public List<PermissionResponse> listPermissions() {
        return sortedPermissions().stream().map(PermissionResponse::from).toList();
    }

    @CacheEvict(cacheNames = CacheNames.USER_BY_USERNAME, allEntries = true)
    @Transactional
    public RoleDetailsResponse create(RoleUpsertRequest request) {
        String roleCode = normalizeRoleCode(request.roleCode(), request.roleName());
        if (OWNER_CODE.equals(roleCode)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "OWNER is reserved for the business owner");
        }
        if (roleRepository.existsByBusinessIdAndRoleCodeIgnoreCase(request.businessId(), roleCode)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Role code already exists for this business");
        }

        Role role = roleRepository.save(Role.builder()
                .businessId(request.businessId())
                .roleCode(roleCode)
                .roleName(request.roleName().trim())
                .description(trimToNull(request.description()))
                .isSystem(false)
                .build());
        replacePermissions(role, request.permissionIds());
        return getRole(request.businessId(), role.getId());
    }

    @CacheEvict(cacheNames = CacheNames.USER_BY_USERNAME, allEntries = true)
    @Transactional
    public RoleDetailsResponse update(Long roleId, RoleUpsertRequest request) {
        Role role = requireRole(request.businessId(), roleId);
        requireCustomRole(role, "OWNER role cannot be modified");
        role.setRoleName(request.roleName().trim());
        role.setDescription(trimToNull(request.description()));
        roleRepository.save(role);
        replacePermissions(role, request.permissionIds());
        return getRole(request.businessId(), role.getId());
    }

    @CacheEvict(cacheNames = CacheNames.USER_BY_USERNAME, allEntries = true)
    @Transactional
    public void delete(Long businessId, Long roleId) {
        Role role = requireRole(businessId, roleId);
        requireCustomRole(role, "OWNER role cannot be deleted");
        if (storeMemberRepository.existsByRole(role)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Role is assigned to staff and cannot be deleted");
        }
        rolePermissionRepository.deleteAll(rolePermissionRepository.findByRole(role));
        roleRepository.delete(role);
    }

    @CacheEvict(cacheNames = CacheNames.USER_BY_USERNAME, allEntries = true)
    @Transactional
    public Role ensureOwnerAccess(Long userId, Long businessId, List<Long> storeIds) {
        User owner = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        Role ownerRole = roleRepository.findByBusinessIdAndRoleCodeIgnoreCase(businessId, OWNER_CODE)
                .orElseGet(() -> roleRepository.save(Role.builder()
                        .businessId(businessId)
                        .roleCode(OWNER_CODE)
                        .roleName("Business Owner")
                        .description("Full access to every page and action")
                        .isSystem(true)
                        .build()));

        if (!Boolean.TRUE.equals(ownerRole.getIsSystem())) {
            ownerRole.setIsSystem(true);
            roleRepository.save(ownerRole);
        }
        addMissingOwnerPermissions(ownerRole);

        for (Long storeId : new LinkedHashSet<>(storeIds)) {
            StoreMember membership = storeMemberRepository.findByUserIdAndStoreId(userId, storeId)
                    .orElseGet(() -> StoreMember.builder().user(owner).storeId(storeId).build());
            membership.setRole(ownerRole);
            storeMemberRepository.save(membership);
        }
        return ownerRole;
    }

    @CacheEvict(cacheNames = CacheNames.USER_BY_USERNAME, allEntries = true)
    @Transactional
    public void assignStaffRole(User user, Long businessId, Long roleId, List<Long> storeIds) {
        Role role = requireRole(businessId, roleId);
        if (isOwner(role)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "OWNER cannot be assigned from staff management");
        }
        Set<Long> desiredStoreIds = storeIds.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (desiredStoreIds.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Select at least one store");
        }

        List<StoreMember> currentMemberships = storeMemberRepository.findByUserAndRoleBusinessId(user, businessId);
        Map<Long, StoreMember> currentByStore = currentMemberships.stream()
                .collect(Collectors.toMap(StoreMember::getStoreId, Function.identity(), (first, ignored) -> first));
        List<StoreMember> removed = currentMemberships.stream()
                .filter(membership -> !desiredStoreIds.contains(membership.getStoreId()))
                .toList();
        if (!removed.isEmpty()) {
            storeMemberRepository.deleteAll(removed);
        }

        for (Long storeId : desiredStoreIds) {
            StoreMember membership = currentByStore.get(storeId);
            if (membership == null) {
                membership = storeMemberRepository.findByUserIdAndStoreId(user.getId(), storeId).orElse(null);
                if (membership != null
                        && membership.getRole() != null
                        && !Objects.equals(membership.getRole().getBusinessId(), businessId)) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Store membership belongs to another business");
                }
            }
            if (membership == null) {
                membership = StoreMember.builder().user(user).storeId(storeId).build();
            }
            membership.setRole(role);
            storeMemberRepository.save(membership);
        }
    }

    private void replacePermissions(Role role, Set<Long> requestedPermissionIds) {
        List<Permission> permissions = permissionRepository.findAllById(requestedPermissionIds);
        if (permissions.size() != requestedPermissionIds.size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "One or more permissions do not exist");
        }
        rolePermissionRepository.deleteAll(rolePermissionRepository.findByRole(role));
        if (!permissions.isEmpty()) {
            rolePermissionRepository.saveAll(permissions.stream()
                    .map(permission -> RolePermission.builder().role(role).permission(permission).build())
                    .toList());
        }
    }

    private void addMissingOwnerPermissions(Role ownerRole) {
        Set<Long> assignedIds = rolePermissionRepository.findByRole(ownerRole).stream()
                .map(RolePermission::getPermission)
                .filter(Objects::nonNull)
                .map(Permission::getId)
                .collect(Collectors.toSet());
        List<RolePermission> missing = permissionRepository.findAll().stream()
                .filter(permission -> !assignedIds.contains(permission.getId()))
                .map(permission -> RolePermission.builder().role(ownerRole).permission(permission).build())
                .toList();
        if (!missing.isEmpty()) {
            rolePermissionRepository.saveAll(missing);
        }
    }

    private Role requireRole(Long businessId, Long roleId) {
        return roleRepository.findByIdAndBusinessId(roleId, businessId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Role not found"));
    }

    private void requireCustomRole(Role role, String message) {
        if (isOwner(role)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, message);
        }
    }

    private boolean isOwner(Role role) {
        return role.getRoleCode() != null && OWNER_CODE.equalsIgnoreCase(role.getRoleCode());
    }

    private List<Permission> sortedPermissions() {
        Comparator<String> textOrder = Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER);
        return permissionRepository.findAll().stream()
                .sorted(Comparator.comparing(Permission::getModuleName, textOrder)
                        .thenComparing(Permission::getPermissionName, textOrder)
                        .thenComparing(Permission::getPermissionCode, textOrder))
                .toList();
    }

    private String normalizeRoleCode(String requestedCode, String roleName) {
        String source = requestedCode == null || requestedCode.isBlank() ? roleName : requestedCode;
        String normalized = source.trim().toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
        if (normalized.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Role code is required");
        }
        return normalized.length() <= 64 ? normalized : normalized.substring(0, 64);
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
