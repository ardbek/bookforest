package com.ebook.bookforest.domain.user.repository;

import com.ebook.bookforest.domain.user.entity.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 사용자 Repository
 */
public interface UserRepository extends JpaRepository<User, Long> {

    /** 사용자 단건 조회(활성 사용자) */
    Optional<User> findByIdAndActiveTrue(Long id);

    /** 사용자 존재 여부 조회(활성 사용자) */
    boolean existsByIdAndActiveTrue(Long userId);
}
