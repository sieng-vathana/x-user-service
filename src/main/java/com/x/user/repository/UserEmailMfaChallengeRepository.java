package com.x.user.repository;

import com.x.user.model.UserEmailMfaChallenge;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Optional;

public interface UserEmailMfaChallengeRepository extends JpaRepository<UserEmailMfaChallenge, Long> {
    Optional<UserEmailMfaChallenge> findByTokenHash(String tokenHash);
    boolean existsByUserIdAndCreatedAtAfter(Long userId, Instant after);
    long deleteByExpiresAtBefore(Instant before);
    void deleteAllByUserId(Long userId);
}
