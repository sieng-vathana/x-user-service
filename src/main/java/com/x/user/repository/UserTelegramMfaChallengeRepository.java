package com.x.user.repository;

import com.x.user.model.UserTelegramMfaChallenge;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Optional;

public interface UserTelegramMfaChallengeRepository extends JpaRepository<UserTelegramMfaChallenge, Long> {
    Optional<UserTelegramMfaChallenge> findByTokenHash(String tokenHash);
    boolean existsByUserIdAndCreatedAtAfter(Long userId, Instant after);
    long deleteByExpiresAtBefore(Instant before);
    void deleteAllByUserId(Long userId);
}
