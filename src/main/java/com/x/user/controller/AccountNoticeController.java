package com.x.user.controller;

import com.sharedlib.response.ApiResponse;
import com.x.user.service.AccountNoticeService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Platform notices emailed to a user, for example a merchant suspension. Internal: called by the BFF only. */
@RestController
@RequestMapping("/internal/users")
@RequiredArgsConstructor
@Validated
public class AccountNoticeController {
    private final AccountNoticeService noticeService;

    @PostMapping("/{userId}/notices")
    public ResponseEntity<ApiResponse<Boolean>> send(@PathVariable @Positive Long userId, @Valid @RequestBody NoticeRequest request) {
        boolean sent = noticeService.send(userId, request.subject(), request.title(), request.message());
        return ResponseEntity.ok(ApiResponse.success(HttpStatus.OK.value(), sent ? "Notice sent" : "Notice skipped", sent));
    }

    public record NoticeRequest(
            @NotBlank @Size(max = 150) String subject,
            @NotBlank @Size(max = 150) String title,
            @NotBlank @Size(max = 4000) String message) {
    }
}
