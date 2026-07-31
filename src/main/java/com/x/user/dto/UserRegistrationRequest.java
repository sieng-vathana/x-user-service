package com.x.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UserRegistrationRequest(
        @NotBlank(message = "Full name is required")
        @Size(max = 120, message = "Full name must not exceed 120 characters") String fullName,
        @NotBlank(message = "Username is required")
        @Size(min = 3, max = 64, message = "Username must be between 3 and 64 characters") String username,
        @NotBlank(message = "Password is required")
        @Size(min = 8, max = 100, message = "Password must be between 8 and 100 characters") String password,
        @Email(message = "Email must be valid")
        @Size(max = 160, message = "Email must not exceed 160 characters") String email,
        @Size(max = 40, message = "Phone number must not exceed 40 characters") String phone) {
}
