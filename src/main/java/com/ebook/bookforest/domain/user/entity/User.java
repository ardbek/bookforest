package com.ebook.bookforest.domain.user.entity;

import com.ebook.bookforest.domain.user.enums.UserRole;
import com.ebook.bookforest.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.*;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
@Entity
@Table(name = "users")
public class User extends BaseTimeEntity {

    /* 사용자 ID */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Long id;

    /* 표시 이름 */
    @Column(name = "display_name", length = 100)
    private String displayName;

    /* 이메일 */
    @Column(name = "email", length = 255)
    private String email;

    /* 프로필 이미지 URL */
    @Column(name = "profile_image_url", length = 1000)
    private String profileImageUrl;

    /* 권한 */
    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 30)
    @Builder.Default
    private UserRole role = UserRole.ROLE_USER;

    /* 활성 여부 */
    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean active = true;

    /* 탈퇴일 */
    @Column(name="withdraw_at")
    private LocalDateTime withdrawAt;

    /* 프로필 수정 */
    public void updateProfile(
        String displayName, String email, String profileImageUrl
    ) {
        this.displayName = displayName;
        this.email = email;
        this.profileImageUrl = profileImageUrl;
    }

    /* 회원 탈퇴 */
    public void withdraw(LocalDateTime withdrawAt) {
        if(!active) {
            throw new IllegalStateException("이미 탈퇴한 사용자입니다.");
        }

        this.active = false;
        this.withdrawAt = withdrawAt;

        // TODO; 탈퇴 정책 정의 후 정리
        this.displayName = null;
        this.email = null;
        this.profileImageUrl = null;
    }
}
