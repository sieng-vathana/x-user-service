package com.x.user.dto;

import com.x.user.model.Permission;
import com.x.user.model.Role;
import com.x.user.model.RolePermission;

import java.util.Comparator;
import java.util.List;
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
        Set<Long> assignedPermissionIds = assignedPermissions.stream()
                .map(RolePermission::getPermission)
                .filter(Objects::nonNull)
                .map(Permission::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Comparator<String> textOrder = Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER);
        List<RolePermissionAccessResponse> permissions = allPermissions.stream()
                .sorted(Comparator.comparing(Permission::getModuleName, textOrder)
                        .thenComparing(Permission::getPermissionName, textOrder)
                        .thenComparing(Permission::getPermissionCode, textOrder))
                .map(permission -> RolePermissionAccessResponse.from(
                        permission,
                        assignedPermissionIds.contains(permission.getId())))
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
