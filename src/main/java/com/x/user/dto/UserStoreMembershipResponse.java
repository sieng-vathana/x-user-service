package com.x.user.dto;

import com.x.user.model.StoreMember;

public record UserStoreMembershipResponse(
        Long storeId,
        Long roleId,
        String roleCode,
        String roleName) {

    public static UserStoreMembershipResponse from(StoreMember membership) {
        var role = membership.getRole();
        return new UserStoreMembershipResponse(
                membership.getStoreId(),
                role == null ? null : role.getId(),
                role == null ? null : role.getRoleCode(),
                role == null ? null : role.getRoleName());
    }
}
