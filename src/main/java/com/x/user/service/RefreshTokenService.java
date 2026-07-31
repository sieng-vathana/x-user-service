package com.x.user.service;

import com.x.user.dto.RegisterRefreshTokenRequest;
import com.x.user.dto.RotateRefreshTokenRequest;
import com.x.user.model.RefreshToken;
import com.x.user.model.User;
import com.x.user.repository.RefreshTokenRepository;
import com.x.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {
    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;

    @Transactional
    public void register(RegisterRefreshTokenRequest request) {
        User user = userRepository.findByUsername(request.username())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        String hash = hash(request.token());
        RefreshToken token = refreshTokenRepository.findByToken(hash)
                .orElseGet(() -> RefreshToken.builder().user(user).token(hash).build());
        token.setUser(user);
        token.setExpiryDate(request.expiresAt());
        token.setRevokedAt(null);
        refreshTokenRepository.save(token);
    }

    @Transactional(readOnly = true)
    public boolean isActive(String rawToken) {
        LocalDateTime now = LocalDateTime.now();
        return refreshTokenRepository.findByToken(hash(rawToken))
                .filter(token -> token.getRevokedAt() == null)
                .filter(token -> token.getExpiryDate().isAfter(now))
                .isPresent();
    }

    @Transactional
    public void rotate(RotateRefreshTokenRequest request) {
        RefreshToken current = requireActive(request.oldToken());
        current.setRevokedAt(LocalDateTime.now());
        refreshTokenRepository.save(current);
        register(new RegisterRefreshTokenRequest(
                current.getUser().getUsername(), request.newToken(), request.expiresAt()));
    }

    @Transactional
    public void revoke(String rawToken) {
        refreshTokenRepository.findByToken(hash(rawToken)).ifPresent(token -> {
            if (token.getRevokedAt() == null) {
                token.setRevokedAt(LocalDateTime.now());
                refreshTokenRepository.save(token);
            }
        });
    }

    private RefreshToken requireActive(String rawToken) {
        LocalDateTime now = LocalDateTime.now();
        return refreshTokenRepository.findByToken(hash(rawToken))
                .filter(token -> token.getRevokedAt() == null)
                .filter(token -> token.getExpiryDate().isAfter(now))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Refresh token is invalid"));
    }

    private String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
