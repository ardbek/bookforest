package com.ebook.bookforest.domain.user.controller;

import com.ebook.bookforest.domain.user.service.UserWithdrawalService;
import com.ebook.bookforest.global.security.jwt.model.JwtPrincipal;
import com.ebook.bookforest.global.security.oauth.RefreshTokenCookieManager;
import jakarta.servlet.http.HttpServletResponse;
import java.net.CookieManager;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserWithdrawalService userWithdrawalService;
    private final RefreshTokenCookieManager cookieManager;

    /**
     * 회원 탈퇴
     * @param principal
     * @param response
     * @return
     */
    @DeleteMapping("/me")
    public ResponseEntity<Void> withdraw(@AuthenticationPrincipal JwtPrincipal principal, HttpServletResponse response) {
        userWithdrawalService.withdraw(principal.userId());
        cookieManager.expire(response);

        return ResponseEntity.noContent().build();
    }
}
