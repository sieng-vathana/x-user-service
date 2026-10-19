package com.x.user.dto;

import java.time.Instant;

public record TelegramMfaChallengeResponse(String challengeToken, Instant expiresAt) {
}
