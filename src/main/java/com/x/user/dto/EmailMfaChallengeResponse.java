package com.x.user.dto;

import java.time.Instant;

public record EmailMfaChallengeResponse(String challengeToken, Instant expiresAt, String maskedEmail) { }
