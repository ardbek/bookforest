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
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
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

    /**
     * 토큰을 소유한 소셜 계정
     * SocialAccount 하나당 Credential도 최대 하나만 존재
     */
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "social_account_id",
        nullable = false,
        foreignKey = @ForeignKey(
            name = "fk_social_oauth_credential_social_account"
        )
    )
    private SocialAccount socialAccount;

    /**
     * 암호화된 외부 플랫폼 Access Token
     */
    @Column(name = "access_token_ciphertext", length = 4096)
    private String accessTokenCiphertext;

    /**
     * 암호화된 외부 플랫폼 Refresh Token
     */
    @Column(name = "refresh_token_ciphertext", length = 4096)
    private String refreshTokenCiphertext;

    /**
     * Access Token 만료 시각
     */
    @Column(name = "access_token_expires_at")
    private Instant accessTokenExpiresAt;

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
