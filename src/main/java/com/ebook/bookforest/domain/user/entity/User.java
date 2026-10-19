package com.ebook.bookforest.domain.user.entity;

import com.ebook.bookforest.domain.user.enums.UserRole;
import com.ebook.bookforest.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 서비스 사용자, 탈퇴해도 삭제하지 않고 개인정보를 지운 비활성 상태로 보존
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "users")
public class User extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Long id;

    /** 소셜 플랫폼에서 받은 표시 이름 */
    @Column(name = "display_name", length = 100)
    private String displayName;

    @Column(name = "email", length = 255)
    private String email;

    @Column(name = "profile_image_url", length = 1000)
    private String profileImageUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 30)
    private UserRole role = UserRole.ROLE_USER;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "withdrawn")
    private LocalDateTime withdrawnAt;

    private User(String displayName, String email, String profileImageUrl) {
        this.displayName = displayName;
        this.email = email;
        this.profileImageUrl = profileImageUrl;
    }

    /** 사용자 생성(소셜 로그인 첫 가입) */
    public static User create(String displayName, String email, String profileImageUrl) {
        return new User(displayName, email, profileImageUrl);
    }

    /** 프로필 수정(소셜 로그인 시 소셜 플랫폼 정보로 갱신) */
    public void updateProfile(String displayName, String email, String profileImageUrl) {
        this.displayName = displayName;
        this.email = email;
        this.profileImageUrl = profileImageUrl;
    }

    /**
     * 회원 탈퇴
     * 개인정보 보호를 위해 표시 이름, 이메일, 프로필 이미지 삭제
     */
    public void withdraw(LocalDateTime withdrawnAt) {
        if (!active) {
            throw new IllegalStateException("이미 탈퇴한 사용자입니다.");
        }

        this.active = false;
        this.withdrawnAt = withdrawnAt;
        this.displayName = null;
        this.email = null;
        this.profileImageUrl = null;
    }
}
