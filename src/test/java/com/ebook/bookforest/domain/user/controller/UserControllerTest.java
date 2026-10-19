package com.ebook.bookforest.domain.user.controller;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ebook.bookforest.domain.searchkeyword.config.SearchKeywordWebConfig;
import com.ebook.bookforest.domain.user.enums.UserRole;
import com.ebook.bookforest.domain.user.service.UserWithdrawalService;
import com.ebook.bookforest.global.security.jwt.JwtAuthenticationFilter;
import com.ebook.bookforest.global.security.jwt.JwtExceptionHandlerFilter;
import com.ebook.bookforest.global.security.jwt.model.JwtPrincipal;
import com.ebook.bookforest.support.RefreshTokenCookieTestConfig;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.test.context.TestSecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 회원 탈퇴 API 계약 검증
 * 실제 쿠키 관리자를 써서 Refresh Token 쿠키 만료 헤더까지 확인
 */
@WebMvcTest(
    controllers = UserController.class,
    excludeFilters = @ComponentScan.Filter(
        type = FilterType.ASSIGNABLE_TYPE,
        classes = {
            JwtAuthenticationFilter.class,
            JwtExceptionHandlerFilter.class,
            SearchKeywordWebConfig.class
        }
    )
)
@AutoConfigureMockMvc(addFilters = false)
@Import(RefreshTokenCookieTestConfig.class)
class UserControllerTest {

    private static final Long USER_ID = 1L;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserWithdrawalService userWithdrawalService;

    @AfterEach
    void tearDown() {
        TestSecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("회원 탈퇴는 로그인 사용자를 탈퇴 처리하고 Refresh Token 쿠키를 만료시킨 뒤 본문 없이 204 응답")
    void withdrawExpiresCookieAndReturnsNoContent() throws Exception {
        // given
        TestSecurityContextHolder.setAuthentication(new UsernamePasswordAuthenticationToken(
            new JwtPrincipal(USER_ID, UserRole.ROLE_USER), null, List.of()
        ));

        // when, then
        mockMvc.perform(delete("/api/users/me"))
            .andExpect(status().isNoContent())
            .andExpect(content().string(""))
            .andExpect(header().string(HttpHeaders.SET_COOKIE, allOf(
                containsString("refresh_token=;"),
                containsString("Max-Age=0"),
                containsString("Path=/api")
            )));

        then(userWithdrawalService).should().withdraw(USER_ID);
    }
}
