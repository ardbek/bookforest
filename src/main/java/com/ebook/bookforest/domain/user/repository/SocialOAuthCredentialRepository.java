package com.ebook.bookforest.domain.user.repository;

import com.ebook.bookforest.domain.user.entity.SocialOAuthCredential;
import com.ebook.bookforest.domain.user.enums.SocialProvider;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SocialOAuthCredentialRepository extends JpaRepository<SocialOAuthCredential, Long> {

    /**
     * 로그인 후 토큰 갱신
     * @param socialAccountId
     * @return
     */
    Optional<SocialOAuthCredential> findBySocialAccount_Id(Long socialAccountId);

    /**
     * 플랫폼 사용자 식별값으로 조회
     * @param socialProvider
     * @param providerSubject
     * @return
     */
    Optional<SocialOAuthCredential> findBySocialAccount_SocialProviderAndSocialAccount_ProviderSubject(SocialProvider socialProvider, String providerSubject);

    /**
     * 탈퇴 전 모든 토큰 조회
     * @param userId
     * @return
     */
    List<SocialOAuthCredential> findAllBySocialAccount_User_Id(Long userId);

    /**
     * 회원 탈퇴 시 Credential 삭제
     * @param userId
     * @return
     */
    long deleteAllBySocialAccount_User_Id(Long userId);
}
