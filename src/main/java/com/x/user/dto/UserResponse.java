package com.x.user.dto;

import com.x.user.model.User;

import java.time.LocalDateTime;

public record UserResponse(
        Long id,
        String username,
        String fullName,
        String gender,
        String phone,
        String email,
        String profileImage,
        Integer status,
        LocalDateTime lastLogin,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getFullName(),
                user.getGender(),
                user.getPhone(),
                user.getEmail(),
                user.getProfileImage(),
                user.getStatus(),
                user.getLastLogin(),
                user.getCreatedAt(),
                user.getUpdatedAt());
    }
}
