package com.ebook.bookforest.domain.user.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.ebook.bookforest.domain.user.enums.UserRole;
import com.ebook.bookforest.domain.user.service.UserWithdrawalService;
import com.ebook.bookforest.global.security.jwt.model.JwtPrincipal;
import com.ebook.bookforest.global.security.oauth.RefreshTokenCookieManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletResponse;

class UserControllerTest {

    private UserWithdrawalService userWithdrawalService;
    private RefreshTokenCookieManager cookieManager;
    private UserController controller;

    @BeforeEach
    void setUp() {
        userWithdrawalService = mock(UserWithdrawalService.class);
        cookieManager = mock(RefreshTokenCookieManager.class);
        controller = new UserController(
            userWithdrawalService,
            cookieManager
        );
    }

    @Test
    void 회원_탈퇴에_성공하면_Refresh_Token_쿠키를_만료시키고_204를_반환한다() {
        JwtPrincipal principal = new JwtPrincipal(
            1L,
            UserRole.ROLE_USER
        );
        MockHttpServletResponse response =
            new MockHttpServletResponse();

        ResponseEntity<Void> result = controller.withdraw(
            principal,
            response
        );

        assertEquals(HttpStatus.NO_CONTENT, result.getStatusCode());
        verify(userWithdrawalService).withdraw(1L);
        verify(cookieManager).expire(response);
    }
}
