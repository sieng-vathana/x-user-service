package com.x.user.dto;

import com.x.user.model.Permission;
import com.x.user.model.Role;
import com.x.user.model.RolePermission;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoleDetailsResponseTest {

    @Test
    void returnsTheCompletePermissionCatalogWithAllowedState() {
        Role role = Role.builder()
                .id(2L)
                .businessId(1L)
                .roleCode("MANAGER")
                .roleName("Store Manager")
                .description("Store operations")
                .isSystem(true)
                .build();
        Permission createProduct = Permission.builder()
                .id(11L)
                .permissionCode("x-product:create")
                .permissionName("Create product")
                .moduleName("PRODUCT")
                .build();
        Permission deleteUser = Permission.builder()
                .id(12L)
                .permissionCode("x-user:delete")
                .permissionName("Delete user")
                .moduleName("USER")
                .build();

        RoleDetailsResponse response = RoleDetailsResponse.from(
                role,
                List.of(deleteUser, createProduct),
                List.of(RolePermission.builder().role(role).permission(createProduct).build()));

        assertEquals("MANAGER", response.roleCode());
        assertEquals(2, response.permissions().size());
        assertEquals("x-product:create", response.permissions().get(0).permissionCode());
        assertTrue(response.permissions().get(0).allowed());
        assertEquals("x-user:delete", response.permissions().get(1).permissionCode());
        assertFalse(response.permissions().get(1).allowed());
    }
}
