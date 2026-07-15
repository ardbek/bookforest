package com.ebook.bookforest.domain.auth.dto;

public record ReissueResponse(
    String accessToken,
    long expiresIn,
    AuthUserResponse user
) {

    public static ReissueResponse from(ReissueResult result) {
        return new ReissueResponse(
            result.accessToken(),
            result.accessTokenExpiresIn(),
            result.user()
        );
    }
}
