package com.x.user.dto;

public record EmailMfaStatusResponse(
        boolean mailConfigured,
        boolean emailAvailable,
        boolean verified,
        boolean enabled,
        String maskedEmail) { }
