package com.x.user.service;

import com.x.redis.cache.CacheNames;
import com.x.user.dto.UserAuthResponse;
import com.x.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Provides the BFF's credential lookup without caching HTTP framework types. */
@Service
@RequiredArgsConstructor
public class UserAuthenticationLookupService {

    private final UserRepository userRepository;

    @Cacheable(cacheNames = CacheNames.USER_BY_USERNAME, key = "#username", unless = "#result == null")
    @Transactional(readOnly = true)
    public UserAuthResponse findByUsername(String username) {
        return userRepository.findByUsername(username)
                .map(user -> UserAuthResponse.builder()
                        .id(user.getId())
                        .username(user.getUsername())
                        .fullName(user.getFullName())
                        .password(user.getPassword())
                        .permissions(userRepository.findPermissionCodesByUserId(user.getId()))
                        .build())
                .orElse(null);
    }
}
