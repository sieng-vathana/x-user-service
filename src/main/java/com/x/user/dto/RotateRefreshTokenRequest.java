package com.x.user.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public record RotateRefreshTokenRequest(
        @NotBlank String oldToken,
        @NotBlank String newToken,
        @NotNull @Future LocalDateTime expiresAt) {
}
