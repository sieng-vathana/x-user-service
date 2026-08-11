package com.x.user.repository;

import com.x.user.model.RolePermission;
import com.x.user.model.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface RolePermissionRepository extends JpaRepository<RolePermission, Long> {
    List<RolePermission> findByRole(Role role);

    List<RolePermission> findByRoleIn(List<Role> roles);
}
