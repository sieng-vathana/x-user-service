package com.x.user.controller;

import com.sharedlib.response.ApiResponse;
import com.x.user.dto.*;
import com.x.user.service.RefreshTokenService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal/users/refresh-tokens")
@RequiredArgsConstructor
public class RefreshTokenController {
    private final RefreshTokenService refreshTokenService;

    @PostMapping
    public ResponseEntity<ApiResponse<Void>> register(
            @Valid @RequestBody RegisterRefreshTokenRequest request) {
        refreshTokenService.register(request);
        return ResponseEntity.ok(ApiResponse.success(200, "Refresh token registered", null));
    }

    @PostMapping("/validate")
    public ResponseEntity<ApiResponse<RefreshTokenStatusResponse>> validate(
            @Valid @RequestBody RefreshTokenValueRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                200, new RefreshTokenStatusResponse(refreshTokenService.isActive(request.token()))));
    }

    @PostMapping("/rotate")
    public ResponseEntity<ApiResponse<Void>> rotate(@Valid @RequestBody RotateRefreshTokenRequest request) {
        refreshTokenService.rotate(request);
        return ResponseEntity.ok(ApiResponse.success(200, "Refresh token rotated", null));
    }

    @PostMapping("/revoke")
    public ResponseEntity<ApiResponse<Void>> revoke(@Valid @RequestBody RefreshTokenValueRequest request) {
        refreshTokenService.revoke(request.token());
        return ResponseEntity.ok(ApiResponse.success(200, "Refresh token revoked", null));
    }
}
