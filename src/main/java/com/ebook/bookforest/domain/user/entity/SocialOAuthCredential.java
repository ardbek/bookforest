package com.ebook.bookforest.domain.user.entity;

import com.ebook.bookforest.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 소셜 연동 해제에 쓰는 소셜 플랫폼 토큰, 소셜 계정당 최대 한 건
 * 토큰 원문은 저장하지 않고 암호문만 보관
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
    name = "social_oauth_credential",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_social_oauth_credential_account",
            columnNames = "social_account_id"
        )
    }
)
public class SocialOAuthCredential extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "social_oauth_credential_id")
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "social_account_id",
        nullable = false,
        foreignKey = @ForeignKey(
            name = "fk_social_oauth_credential_social_account"
        )
    )
    private SocialAccount socialAccount;

    /** 암호화한 소셜 플랫폼 Access Token */
    @Column(name = "access_token_ciphertext", length = 4096)
    private String accessTokenCiphertext;

    /** 암호화한 소셜 플랫폼 Refresh Token */
    @Column(name = "refresh_token_ciphertext", length = 4096)
    private String refreshTokenCiphertext;

    @Column(name = "access_token_expires_at")
    private Instant accessTokenExpiresAt;

    private SocialOAuthCredential(SocialAccount socialAccount) {
        this.socialAccount = Objects.requireNonNull(socialAccount, "소셜 계정은 필수입니다.");
    }

    /** 소셜 플랫폼 토큰 정보 생성, 토큰은 갱신 메서드로 저장 */
    public static SocialOAuthCredential create(SocialAccount socialAccount) {
        return new SocialOAuthCredential(socialAccount);
    }

    public void updateAccessToken(
        String accessTokenCiphertext,
        Instant accessTokenExpiresAt
    ) {
        this.accessTokenCiphertext = accessTokenCiphertext;
        this.accessTokenExpiresAt = accessTokenExpiresAt;
    }

    public void updateRefreshToken(String refreshTokenCiphertext) {
        this.refreshTokenCiphertext = refreshTokenCiphertext;
    }

}
