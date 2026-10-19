package com.ebook.bookforest.domain.user.client;

import com.ebook.bookforest.domain.user.enums.SocialProvider;

/**
 * 소셜 플랫폼 연동 해제 API 클라이언트
 */
public interface SocialUnlinkClient {

    boolean supports(SocialProvider provider);

    /**
     * 연동 해제 요청
     *
     * @throws SocialUnlinkClientException API 호출이 실패한 경우, 재시도 가능 여부 포함
     * @throws IllegalArgumentException 명령의 플랫폼이 다르거나 필수값이 없는 경우
     */
    void unlink(SocialUnlinkCommand command);
}
