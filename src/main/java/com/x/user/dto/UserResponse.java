package com.x.user.dto;

import com.x.user.model.User;
import com.x.user.model.StoreMember;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

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
        LocalDateTime updatedAt,
        List<String> roles) {

    public static UserResponse from(User user) {
        return from(user, List.of());
    }

    public static UserResponse from(User user, List<StoreMember> memberships) {
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
                user.getUpdatedAt(),
                memberships.stream()
                        .map(StoreMember::getRole)
                        .filter(Objects::nonNull)
                        .map(role -> role.getRoleCode())
                        .filter(Objects::nonNull)
                        .distinct()
                        .toList());
    }
}
