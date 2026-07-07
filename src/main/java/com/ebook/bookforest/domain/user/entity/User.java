package com.ebook.bookforest.domain.user.entity;

import com.ebook.bookforest.domain.user.enums.UserRole;
import com.ebook.bookforest.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
@Entity
@Table(name = "user")
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

    public void updateProfile(
        String displayName,
        String email,
        String profileImageUrl
    ) {
        this.displayName = displayName;
        this.email = email;
        this.profileImageUrl = profileImageUrl;
    }

    public void deactivate() {
        this.active = false;
    }

    public void activate() {
        this.active = true;
    }
}