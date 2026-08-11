package com.x.user.repository;

import com.x.user.model.Role;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RoleRepository extends JpaRepository<Role, Long> {
    List<Role> findAllByBusinessIdOrderByIsSystemDescRoleNameAsc(Long businessId);

    Optional<Role> findByIdAndBusinessId(Long id, Long businessId);

    Optional<Role> findByBusinessIdAndRoleCodeIgnoreCase(Long businessId, String roleCode);

    boolean existsByBusinessIdAndRoleCodeIgnoreCase(Long businessId, String roleCode);
}
