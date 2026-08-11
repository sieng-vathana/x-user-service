package com.x.user.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserAuthResponse {
    private Long id;
    private String username;
    private String fullName;
    private String email;
    private String password;
    private Set<String> permissions;
    private Set<Long> businessIds;
    private Set<Long> storeIds;
}
