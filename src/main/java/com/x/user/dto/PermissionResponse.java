package com.x.user.dto;

import com.x.user.model.Permission;

public record PermissionResponse(
        Long id,
        String permissionCode,
        String permissionName,
        String moduleName,
        String description) {

    public static PermissionResponse from(Permission permission) {
        return new PermissionResponse(
                permission.getId(),
                permission.getPermissionCode(),
                permission.getPermissionName(),
                permission.getModuleName(),
                permission.getDescription());
    }
}
