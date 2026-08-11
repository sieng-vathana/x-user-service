package com.x.user.repository;

import com.x.user.model.StoreMember;
import com.x.user.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface StoreMemberRepository extends JpaRepository<StoreMember, Long> {
    List<StoreMember> findByUser(User user);

    List<StoreMember> findByUserIn(Collection<User> users);

    List<StoreMember> findByUserInAndRoleBusinessId(Collection<User> users, Long businessId);

    List<StoreMember> findByUserAndRoleBusinessId(User user, Long businessId);

    Optional<StoreMember> findByUserIdAndStoreId(Long userId, Long storeId);

    boolean existsByRole(com.x.user.model.Role role);

    boolean existsByUserIdAndStoreId(Long userId, Long storeId);

    boolean existsByUserUsernameAndStoreId(String username, Long storeId);
}
