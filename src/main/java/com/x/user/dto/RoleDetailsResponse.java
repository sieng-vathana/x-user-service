package com.x.user.dto;

import com.x.user.model.Permission;
import com.x.user.model.Role;
import com.x.user.model.RolePermission;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public record RoleDetailsResponse(
        Long id,
        Long businessId,
        String roleCode,
        String roleName,
        String description,
        Boolean isSystem,
        List<RolePermissionAccessResponse> permissions) {

    public static RoleDetailsResponse from(
            Role role,
            List<Permission> allPermissions,
            List<RolePermission> assignedPermissions) {
        Set<String> assignedPermissionCodes = assignedPermissions.stream()
                .map(RolePermission::getPermission)
                .filter(Objects::nonNull)
                .map(Permission::getPermissionCode)
                .filter(Objects::nonNull)
                .map(code -> code.toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());

        Comparator<String> textOrder = Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER);
        Map<String, Permission> uniqueByCode = new LinkedHashMap<>();
        allPermissions.stream()
                .sorted(Comparator.comparing(Permission::getModuleName, textOrder)
                        .thenComparing(Permission::getPermissionName, textOrder)
                        .thenComparing(Permission::getPermissionCode, textOrder))
                .filter(permission -> permission.getPermissionCode() != null
                        && !permission.getPermissionCode().isBlank())
                .forEach(permission -> uniqueByCode.putIfAbsent(
                        permission.getPermissionCode().toLowerCase(Locale.ROOT),
                        permission));
        List<RolePermissionAccessResponse> permissions = uniqueByCode.values().stream()
                .map(permission -> RolePermissionAccessResponse.from(
                        permission,
                        assignedPermissionCodes.contains(permission.getPermissionCode().toLowerCase(Locale.ROOT))))
                .toList();

        return new RoleDetailsResponse(
                role.getId(),
                role.getBusinessId(),
                role.getRoleCode(),
                role.getRoleName(),
                role.getDescription(),
                role.getIsSystem(),
                permissions);
    }
}
