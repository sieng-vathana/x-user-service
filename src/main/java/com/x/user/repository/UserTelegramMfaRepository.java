package com.x.user.repository;

import com.x.user.model.UserTelegramMfa;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserTelegramMfaRepository extends JpaRepository<UserTelegramMfa, Long> {
    Optional<UserTelegramMfa> findByUserId(Long userId);
    Optional<UserTelegramMfa> findByLinkCodeHash(String linkCodeHash);
    boolean existsByChatIdAndUserIdNot(Long chatId, Long userId);
}
