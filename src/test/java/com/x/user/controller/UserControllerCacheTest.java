package com.x.user.controller;

import com.x.redis.cache.CacheNames;
import com.x.user.dto.UserRegistrationRequest;
import org.junit.jupiter.api.Test;
import org.springframework.cache.annotation.CacheEvict;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserControllerCacheTest {

    @Test
    void registrationEvictsAnyExistingAuthenticationEntry() throws NoSuchMethodException {
        CacheEvict cacheEvict = UserController.class
                .getMethod("register", UserRegistrationRequest.class)
                .getAnnotation(CacheEvict.class);

        assertEquals(List.of(CacheNames.USER_BY_USERNAME), List.of(cacheEvict.cacheNames()));
        assertEquals("#request.username().trim()", cacheEvict.key());
    }

    @Test
    void assigningAStoreRoleClearsAuthenticationEntriesContainingPermissions() throws NoSuchMethodException {
        CacheEvict cacheEvict = UserController.class
                .getMethod("assignOwnerStoreMembership", Long.class, Long.class)
                .getAnnotation(CacheEvict.class);

        assertEquals(List.of(CacheNames.USER_BY_USERNAME), List.of(cacheEvict.cacheNames()));
        assertTrue(cacheEvict.allEntries());
    }
}
