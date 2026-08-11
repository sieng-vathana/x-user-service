package com.x.user.dto;

import com.x.user.model.Permission;

public record RolePermissionAccessResponse(
        Long id,
        String permissionCode,
        String permissionName,
        String moduleName,
        String description,
        boolean allowed) {

    public static RolePermissionAccessResponse from(Permission permission, boolean allowed) {
        return new RolePermissionAccessResponse(
                permission.getId(),
                permission.getPermissionCode(),
                permission.getPermissionName(),
                permission.getModuleName(),
                permission.getDescription(),
                allowed);
    }
}
