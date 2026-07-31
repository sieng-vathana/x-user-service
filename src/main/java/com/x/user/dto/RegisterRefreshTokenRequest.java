package com.x.user.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public record RegisterRefreshTokenRequest(
        @NotBlank String username,
        @NotBlank String token,
        @NotNull @Future LocalDateTime expiresAt) {
}
