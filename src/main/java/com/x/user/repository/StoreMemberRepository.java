package com.x.user.repository;

import com.x.user.model.StoreMember;
import com.x.user.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface StoreMemberRepository extends JpaRepository<StoreMember, Long> {
    List<StoreMember> findByUser(User user);

    boolean existsByUserIdAndStoreId(Long userId, Long storeId);

    boolean existsByUserUsernameAndStoreId(String username, Long storeId);
}
