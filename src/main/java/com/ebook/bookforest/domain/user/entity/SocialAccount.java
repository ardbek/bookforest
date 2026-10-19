package com.ebook.bookforest.domain.user.entity;

import com.ebook.bookforest.domain.user.enums.SocialProvider;
import com.ebook.bookforest.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 사용자에 연결된 소셜 로그인 계정, 소셜 플랫폼과 사용자 식별값 조합당 한 건
 * 회원 탈퇴 시 삭제
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
    name = "social_account",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_social_provider_subject",
            columnNames = {"social_provider", "provider_subject"}
        )
    }
)
public class SocialAccount extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "social_account_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "social_provider", nullable = false, length = 30)
    private SocialProvider socialProvider;

    /** 소셜 플랫폼이 부여한 사용자 식별값 */
    @Column(name = "provider_subject", nullable = false, length = 200)
    private String providerSubject;

    @Column(name = "email", length = 255)
    private String email;

    @Column(name = "nickname", length = 100)
    private String nickname;

    @Column(name = "profile_image_url", length = 1000)
    private String profileImageUrl;

    @Builder
    private SocialAccount(
        User user,
        SocialProvider socialProvider,
        String providerSubject,
        String email,
        String nickname,
        String profileImageUrl
    ) {
        this.user = Objects.requireNonNull(user, "사용자는 필수입니다.");
        this.socialProvider = Objects.requireNonNull(socialProvider, "소셜 플랫폼은 필수입니다.");
        this.providerSubject = Objects.requireNonNull(providerSubject, "소셜 사용자 식별값은 필수입니다.");
        this.email = email;
        this.nickname = nickname;
        this.profileImageUrl = profileImageUrl;
    }

    /** 프로필 갱신(로그인할 때마다 소셜 플랫폼 정보로) */
    public void updateProfile(String nickname, String email, String profileImageUrl) {
        this.nickname = nickname;
        this.email = email;
        this.profileImageUrl = profileImageUrl;
    }
}
