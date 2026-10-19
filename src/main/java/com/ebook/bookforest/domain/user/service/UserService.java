package com.ebook.bookforest.domain.user.service;

import com.ebook.bookforest.domain.user.dto.UserInfo;
import com.ebook.bookforest.domain.user.repository.UserRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 다른 도메인에 제공하는 사용자 조회 서비스
 */
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    /**
     * 사용자 정보 단건 조회(활성 사용자)
     * 없거나 탈퇴한 사용자일 때 어떤 오류로 처리할지는 호출하는 쪽이 결정
     */
    @Transactional(readOnly = true)
    public Optional<UserInfo> findActiveUserInfo(Long userId) {
        return userRepository.findByIdAndActiveTrue(userId)
            .map(UserInfo::from);
    }

    /**
     * 활성 사용자 여부 확인(인증 필터용)
     * 요청마다 호출되므로 엔티티 조회 없이 존재 여부만 확인
     */
    @Transactional(readOnly = true)
    public boolean isActiveUser(Long userId) {
        return userRepository.existsByIdAndActiveTrue(userId);
    }
}
