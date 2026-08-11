package com.x.user.controller;

import com.x.user.repository.RefreshTokenRepository;
import com.x.user.repository.RoleRepository;
import com.x.user.repository.StoreMemberRepository;
import com.x.user.repository.UserRepository;
import com.x.user.service.RoleManagementService;
import com.x.user.service.UserAuthenticationLookupService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserControllerBusinessPagingTest {

    @Test
    void businessUserQueryDoesNotApplySortToTheStoreMembershipRoot() {
        UserRepository userRepository = mock(UserRepository.class);
        when(userRepository.findAllByBusinessId(eq(2L), any(Pageable.class)))
                .thenReturn(Page.empty());
        UserController controller = new UserController(
                userRepository,
                mock(StoreMemberRepository.class),
                mock(RoleRepository.class),
                mock(RefreshTokenRepository.class),
                mock(UserAuthenticationLookupService.class),
                mock(RoleManagementService.class));

        controller.getAllUsers(2L, 0, 50);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(userRepository).findAllByBusinessId(eq(2L), pageable.capture());
        assertTrue(pageable.getValue().getSort().isUnsorted());
    }
}
