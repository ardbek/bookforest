package com.ebook.bookforest.domain.user.repository;

import com.ebook.bookforest.domain.user.entity.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * 활성화 된 회원 찾기
     * @param id
     * @return
     */
    Optional<User> findByIdAndActiveTrue(Long id);

    /**
     * 회원 활성 여부 조회
     * @param userId
     * @return
     */
    boolean existsByIdAndActiveTrue(Long userId);
}
