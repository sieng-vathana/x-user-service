package com.x.user.repository;

import com.x.user.model.StoreMember;
import com.x.user.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Collection;
import java.util.List;

public interface StoreMemberRepository extends JpaRepository<StoreMember, Long> {
    List<StoreMember> findByUser(User user);

    List<StoreMember> findByUserIn(Collection<User> users);

    boolean existsByUserIdAndStoreId(Long userId, Long storeId);

    boolean existsByUserUsernameAndStoreId(String username, Long storeId);
}
