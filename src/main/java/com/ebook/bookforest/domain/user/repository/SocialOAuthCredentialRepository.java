package com.ebook.bookforest.domain.user.repository;

import com.ebook.bookforest.domain.user.entity.SocialOAuthCredential;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 소셜 플랫폼 토큰 Repository
 */
public interface SocialOAuthCredentialRepository extends JpaRepository<SocialOAuthCredential, Long> {

    Optional<SocialOAuthCredential> findBySocialAccount_Id(Long socialAccountId);

    /** 사용자의 모든 소셜 플랫폼 토큰 조회(회원 탈퇴 시 연동 해제 작업 생성용) */
    List<SocialOAuthCredential> findAllBySocialAccount_User_Id(Long userId);

    long deleteAllBySocialAccount_User_Id(Long userId);
}
