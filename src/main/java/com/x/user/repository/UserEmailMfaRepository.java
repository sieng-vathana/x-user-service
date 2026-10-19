package com.x.user.repository;

import com.x.user.model.UserEmailMfa;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserEmailMfaRepository extends JpaRepository<UserEmailMfa, Long> {
    Optional<UserEmailMfa> findByUserId(Long userId);
}
