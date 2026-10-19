package com.x.user.dto;

import java.time.Instant;

public record TelegramMfaLinkResponse(String url, Instant expiresAt) {
}
