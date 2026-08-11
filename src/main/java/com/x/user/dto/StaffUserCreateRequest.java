package com.x.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

public record StaffUserCreateRequest(
        @NotNull @Positive Long businessId,
        @NotNull @Positive Long roleId,
        @NotEmpty List<@Positive Long> storeIds,
        @NotBlank @Size(max = 120) String fullName,
        @NotBlank @Size(min = 3, max = 64) String username,
        @NotBlank @Size(min = 8, max = 100) String password,
        @Email @Size(max = 160) String email,
        @Size(max = 40) String phone,
        Integer status) {
}
