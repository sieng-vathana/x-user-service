package com.x.user.dto;

import jakarta.validation.constraints.NotBlank;

public record RefreshTokenValueRequest(@NotBlank String token) {
}
