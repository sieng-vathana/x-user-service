package com.x.user.dto;

import com.x.user.model.Role;

import java.time.LocalDateTime;

public record RoleSummaryResponse(
        Long id,
        Long businessId,
        String roleCode,
        String roleName,
        String description,
        Boolean isSystem,
        long permissionCount,
        LocalDateTime createdAt) {

    public static RoleSummaryResponse from(Role role, long permissionCount) {
        return new RoleSummaryResponse(
                role.getId(),
                role.getBusinessId(),
                role.getRoleCode(),
                role.getRoleName(),
                role.getDescription(),
                role.getIsSystem(),
                permissionCount,
                role.getCreatedAt());
    }
}
