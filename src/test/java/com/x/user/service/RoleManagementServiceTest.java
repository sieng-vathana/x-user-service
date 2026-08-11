package com.x.user.service;

import com.x.user.model.Permission;
import com.x.user.model.Role;
import com.x.user.model.RolePermission;
import com.x.user.repository.PermissionRepository;
import com.x.user.repository.RolePermissionRepository;
import com.x.user.repository.RoleRepository;
import com.x.user.repository.StoreMemberRepository;
import com.x.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoleManagementServiceTest {

    @Mock private RoleRepository roleRepository;
    @Mock private PermissionRepository permissionRepository;
    @Mock private RolePermissionRepository rolePermissionRepository;
    @Mock private StoreMemberRepository storeMemberRepository;
    @Mock private UserRepository userRepository;
    @InjectMocks private RoleManagementService service;

    @Test
    void ownerSummaryAlwaysCountsTheCompletePermissionCatalog() {
        Role owner = role(1L, "OWNER", "Business Owner");
        Role cashier = role(2L, "CASHIER", "Cashier");
        Permission readProduct = Permission.builder().id(10L).build();
        when(roleRepository.findAllByBusinessIdOrderByIsSystemDescRoleNameAsc(7L))
                .thenReturn(List.of(owner, cashier));
        when(rolePermissionRepository.findByRoleIn(List.of(owner, cashier)))
                .thenReturn(List.of(RolePermission.builder().role(cashier).permission(readProduct).build()));
        when(permissionRepository.count()).thenReturn(12L);

        var result = service.listRoles(7L);

        assertEquals(12L, result.get(0).permissionCount());
        assertEquals(1L, result.get(1).permissionCount());
    }

    @Test
    void ownerCannotBeDeleted() {
        Role owner = role(1L, "OWNER", "Business Owner");
        when(roleRepository.findByIdAndBusinessId(1L, 7L)).thenReturn(Optional.of(owner));

        assertThrows(ResponseStatusException.class, () -> service.delete(7L, 1L));

        verify(roleRepository, never()).delete(owner);
    }

    private Role role(Long id, String code, String name) {
        return Role.builder()
                .id(id)
                .businessId(7L)
                .roleCode(code)
                .roleName(name)
                .isSystem("OWNER".equals(code))
                .build();
    }
}
