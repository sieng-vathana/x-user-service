package com.x.user.dto;

public record TelegramMfaStatusResponse(
        boolean botConfigured,
        String botUsername,
        boolean connected,
        boolean enabled,
        boolean linkPending) {
}
