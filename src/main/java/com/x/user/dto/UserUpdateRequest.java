package com.x.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

public record UserUpdateRequest(
        @Size(max = 120, message = "Full name must not exceed 120 characters") String fullName,
        @Email(message = "Email must be valid")
        @Size(max = 160, message = "Email must not exceed 160 characters") String email,
        @Size(max = 40, message = "Phone number must not exceed 40 characters") String phone,
        @Size(min = 8, max = 100, message = "Password must be between 8 and 100 characters") String password,
        Integer status,
        @Positive Long businessId,
        @Positive Long roleId,
        List<@Positive Long> storeIds) {
}
