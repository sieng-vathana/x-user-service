package com.x.user.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "user_telegram_mfa", uniqueConstraints = {
        @UniqueConstraint(name = "uk_user_telegram_mfa_user", columnNames = "user_id"),
        @UniqueConstraint(name = "uk_user_telegram_mfa_chat", columnNames = "chat_id")
})
@Getter
@Setter
@NoArgsConstructor
public class UserTelegramMfa {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "chat_id")
    private Long chatId;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "link_code_hash", length = 64)
    private String linkCodeHash;

    @Column(name = "link_expires_at")
    private Instant linkExpiresAt;

    @Column(name = "linked_at")
    private Instant linkedAt;
}
