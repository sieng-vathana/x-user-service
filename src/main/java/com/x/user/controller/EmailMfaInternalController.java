package com.x.user.controller;

import com.sharedlib.response.ApiResponse;
import com.x.user.dto.EmailMfaChallengeResponse;
import com.x.user.dto.EmailMfaStatusResponse;
import com.x.user.service.EmailMfaService;
import jakarta.validation.Valid;
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
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/users")
@RequiredArgsConstructor
@Validated
public class EmailMfaInternalController {
    private final EmailMfaService emailMfaService;

    @GetMapping("/{userId}/email-mfa")
    public ResponseEntity<ApiResponse<EmailMfaStatusResponse>> status(@PathVariable @Positive Long userId) {
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), emailMfaService.status(userId)));
    }

    @PostMapping("/{userId}/email-mfa/verification")
    public ResponseEntity<ApiResponse<EmailMfaChallengeResponse>> startSetup(@PathVariable @Positive Long userId) {
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), emailMfaService.startSetup(userId)));
    }

    @PostMapping("/{userId}/email-mfa/verification/confirm")
    public ResponseEntity<ApiResponse<Boolean>> verifySetup(
            @PathVariable @Positive Long userId, @Valid @RequestBody EmailMfaVerifyRequest request) {
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(),
                emailMfaService.verifySetup(userId, request.challengeToken(), request.code())));
    }

    @PutMapping("/{userId}/email-mfa/enabled")
    public ResponseEntity<ApiResponse<EmailMfaStatusResponse>> setEnabled(
            @PathVariable @Positive Long userId, @Valid @RequestBody EmailEnabledRequest request) {
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), emailMfaService.setEnabled(userId, request.enabled())));
    }

    @DeleteMapping("/{userId}/email-mfa")
    public ResponseEntity<ApiResponse<EmailMfaStatusResponse>> disconnect(@PathVariable @Positive Long userId) {
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), emailMfaService.disconnect(userId)));
    }

    @PostMapping("/{userId}/email-mfa/challenges")
    public ResponseEntity<ApiResponse<EmailMfaChallengeResponse>> startLogin(@PathVariable @Positive Long userId) {
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), emailMfaService.startLogin(userId)));
    }

    @PostMapping("/email-mfa/verify")
    public ResponseEntity<ApiResponse<Boolean>> verifyLogin(@Valid @RequestBody EmailMfaLoginVerifyRequest request) {
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(),
                emailMfaService.verifyLogin(request.userId(), request.challengeToken(), request.code())));
    }

    public record EmailMfaVerifyRequest(@jakarta.validation.constraints.NotBlank String challengeToken,
                                        @jakarta.validation.constraints.Pattern(regexp = "\\d{8}") String code) { }
    public record EmailMfaLoginVerifyRequest(@Positive Long userId,
                                             @jakarta.validation.constraints.NotBlank String challengeToken,
                                             @jakarta.validation.constraints.Pattern(regexp = "\\d{8}") String code) { }
    public record EmailEnabledRequest(boolean enabled) { }
}
