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
        return findByUsername(username, null);
    }

    @Cacheable(
            cacheNames = CacheNames.USER_BY_USERNAME,
            key = "#username + ':business:' + #businessId",
            unless = "#result == null")
    @Transactional(readOnly = true)
    public UserAuthResponse findByUsername(String username, Long businessId) {
        return userRepository.findByUsername(username)
                .map(user -> UserAuthResponse.builder()
                        .id(user.getId())
                        .username(user.getUsername())
                        .fullName(user.getFullName())
                        .email(user.getEmail())
                        .password(user.getPassword())
                        .permissions(businessId == null
                                ? userRepository.findPermissionCodesByUserId(user.getId())
                                : userRepository.findPermissionCodesByUserIdAndBusinessId(user.getId(), businessId))
                        .businessIds(userRepository.findBusinessIdsByUserId(user.getId()))
                        .storeIds(businessId == null
                                ? java.util.Set.of()
                                : userRepository.findStoreIdsByUserIdAndBusinessId(user.getId(), businessId))
                        .build())
                .orElse(null);
    }
}
