package com.x.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record TelegramMfaVerifyRequest(
        @NotNull @Positive Long userId,
        @NotBlank String challengeToken,
        @NotBlank String code) {
}
