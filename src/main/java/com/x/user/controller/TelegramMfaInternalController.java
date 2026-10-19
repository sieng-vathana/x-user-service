package com.x.user.controller;

import com.sharedlib.response.ApiResponse;
import com.x.user.dto.TelegramMfaChallengeResponse;
import com.x.user.dto.TelegramMfaLinkResponse;
import com.x.user.dto.TelegramMfaStatusResponse;
import com.x.user.dto.TelegramMfaVerifyRequest;
import com.x.user.service.TelegramMfaService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/users")
@RequiredArgsConstructor
@Validated
public class TelegramMfaInternalController {
    private final TelegramMfaService telegramMfaService;

    @GetMapping("/{userId}/telegram-mfa")
    public ResponseEntity<ApiResponse<TelegramMfaStatusResponse>> status(@PathVariable @Positive Long userId) {
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), telegramMfaService.status(userId)));
    }

    @PostMapping("/{userId}/telegram-mfa/link")
    public ResponseEntity<ApiResponse<TelegramMfaLinkResponse>> createLink(@PathVariable @Positive Long userId) {
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), telegramMfaService.createLink(userId)));
    }

    @PutMapping("/{userId}/telegram-mfa/enabled")
    public ResponseEntity<ApiResponse<TelegramMfaStatusResponse>> setEnabled(
            @PathVariable @Positive Long userId, @Valid @RequestBody TelegramEnabledRequest request) {
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), telegramMfaService.setEnabled(userId, request.enabled())));
    }

    @DeleteMapping("/{userId}/telegram-mfa")
    public ResponseEntity<ApiResponse<TelegramMfaStatusResponse>> disconnect(@PathVariable @Positive Long userId) {
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), telegramMfaService.disconnect(userId)));
    }

    @PostMapping("/{userId}/telegram-mfa/challenges")
    public ResponseEntity<ApiResponse<TelegramMfaChallengeResponse>> startLogin(
            @PathVariable @Positive Long userId, @RequestParam @NotBlank String username) {
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), telegramMfaService.startLogin(userId, username)));
    }

    @PostMapping("/telegram-mfa/verify")
    public ResponseEntity<ApiResponse<Boolean>> verify(@Valid @RequestBody TelegramMfaVerifyRequest request) {
        boolean verified = telegramMfaService.verify(request.userId(), request.challengeToken(), request.code());
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), verified));
    }

    public record TelegramEnabledRequest(boolean enabled) { }
}
