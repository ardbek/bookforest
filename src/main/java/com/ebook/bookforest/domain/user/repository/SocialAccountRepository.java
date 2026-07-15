package com.ebook.bookforest.domain.user.repository;

import com.ebook.bookforest.domain.user.entity.SocialAccount;
import com.ebook.bookforest.domain.user.enums.SocialProvider;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SocialAccountRepository extends JpaRepository<SocialAccount, Long> {

    @EntityGraph(attributePaths = "user")
    Optional<SocialAccount> findBySocialProviderAndProviderSubject(SocialProvider socialProvider, String providerSubject);

    boolean existsByUser_IdAndSocialProvider(Long userId, SocialProvider socialProvider);

    List<SocialAccount> findAllByUser_Id(Long userId);

    long countByUser_Id(Long userId);

    long deleteAllByUser_Id(Long userId);
}
