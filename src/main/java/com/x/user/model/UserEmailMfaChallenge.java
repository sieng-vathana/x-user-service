package com.x.user.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(name = "user_email_mfa_challenges", indexes = {
        @Index(name = "ix_email_mfa_challenge_user_created", columnList = "user_id, created_at"),
        @Index(name = "ix_email_mfa_challenge_expiry", columnList = "expires_at")
})
@Getter
@Setter
@NoArgsConstructor
public class UserEmailMfaChallenge {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "code_hash", nullable = false, length = 100)
    private String codeHash;

    @Column(name = "email_address", nullable = false, length = 254)
    private String emailAddress;

    @Column(nullable = false, length = 16)
    private String purpose;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(nullable = false)
    private int attempts;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
