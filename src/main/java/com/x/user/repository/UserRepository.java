package com.x.user.repository;

import com.x.user.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;
import java.util.Set;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);

    @Query(
            value = """
                    SELECT u
                    FROM User u
                    WHERE EXISTS (
                        SELECT 1
                        FROM StoreMember sm
                        WHERE sm.user = u
                          AND sm.role.businessId = :businessId
                    )
                    ORDER BY u.id ASC
                    """,
            countQuery = """
                    SELECT COUNT(u.id)
                    FROM User u
                    WHERE EXISTS (
                        SELECT 1
                        FROM StoreMember sm
                        WHERE sm.user = u
                          AND sm.role.businessId = :businessId
                    )
                    """)
    Page<User> findAllByBusinessId(@Param("businessId") Long businessId, Pageable pageable);

    /**
     * Permission codes for a user (one SQL round-trip).
     */
    @Query(value = """
            SELECT DISTINCT p.permission_code
            FROM store_members sm
            INNER JOIN roles r ON sm.role_id = r.id
            INNER JOIN role_permissions rp ON rp.role_id = r.id
            INNER JOIN permissions p ON rp.permission_id = p.id
            WHERE sm.user_id = :userId
              AND p.permission_code IS NOT NULL
              AND p.permission_code <> ''
            """, nativeQuery = true)
    Set<String> findPermissionCodesByUserId(@Param("userId") Long userId);

    @Query(value = """
            SELECT DISTINCT p.permission_code
            FROM permissions p
            WHERE p.permission_code IS NOT NULL
              AND p.permission_code <> ''
              AND EXISTS (
                  SELECT 1
                  FROM store_members sm
                  INNER JOIN roles r ON sm.role_id = r.id
                  LEFT JOIN role_permissions rp
                    ON rp.role_id = r.id
                   AND rp.permission_id = p.id
                  WHERE sm.user_id = :userId
                    AND r.business_id = :businessId
                    AND (UPPER(r.role_code) = 'OWNER' OR rp.permission_id IS NOT NULL)
              )
            """, nativeQuery = true)
    Set<String> findPermissionCodesByUserIdAndBusinessId(
            @Param("userId") Long userId,
            @Param("businessId") Long businessId);

    @Query(value = """
            SELECT DISTINCT r.business_id
            FROM store_members sm
            INNER JOIN roles r ON sm.role_id = r.id
            WHERE sm.user_id = :userId
              AND r.business_id IS NOT NULL
            """, nativeQuery = true)
    Set<Long> findBusinessIdsByUserId(@Param("userId") Long userId);

    @Query(value = """
            SELECT DISTINCT sm.store_id
            FROM store_members sm
            INNER JOIN roles r ON sm.role_id = r.id
            WHERE sm.user_id = :userId
              AND r.business_id = :businessId
              AND sm.store_id IS NOT NULL
            """, nativeQuery = true)
    Set<Long> findStoreIdsByUserIdAndBusinessId(
            @Param("userId") Long userId,
            @Param("businessId") Long businessId);
}
