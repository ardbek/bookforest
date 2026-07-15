package com.ebook.bookforest.domain.user.entity;

import com.ebook.bookforest.domain.user.enums.SocialProvider;
import com.ebook.bookforest.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
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

    /* 소셜 ID */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "social_account_id")
    private Long id;

    /* 회원 ID */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /* 소셜 로그인 플랫폼 */
    @Enumerated(EnumType.STRING)
    @Column(name = "social_provider", nullable = false, length = 30)
    private SocialProvider socialProvider;

    @Column(name = "provider_subject", nullable = false, length = 200)
    private String providerSubject;

    @Column(name = "email", length = 255)
    private String email;

    @Column(name = "nickname", length = 100)
    private String nickname;

    @Column(name = "profile_image_url", length = 1000)
    private String profileImageUrl;

    public void updateProfile(
        String email,
        String nickname,
        String profileImageUrl
    ) {
        this.email = email;
        this.nickname = nickname;
        this.profileImageUrl = profileImageUrl;
    }
}