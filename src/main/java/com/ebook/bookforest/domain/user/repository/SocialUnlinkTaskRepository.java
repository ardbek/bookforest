package com.ebook.bookforest.domain.user.repository;

import com.ebook.bookforest.domain.user.entity.SocialUnlinkTask;
import com.ebook.bookforest.domain.user.enums.SocialProvider;
import com.ebook.bookforest.domain.user.enums.SocialUnlinkStatus;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 소셜 연동 해제 작업 Repository
 */
public interface SocialUnlinkTaskRepository extends JpaRepository<SocialUnlinkTask, Long> {

    /** 연동 해제 작업 단건 조회(비관적 쓰기 락), 여러 서버의 동시 처리 방지 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select task
        from SocialUnlinkTask task
        where task.id = :taskId
        """)
    Optional<SocialUnlinkTask> findByIdForUpdate(@Param("taskId") Long taskId);

    /** 연동 해제 작업 존재 여부 조회(소셜 계정별, 상태별), 해제 중인 계정의 재가입 차단용 */
    boolean existsBySocialProviderAndProviderSubjectAndStatusIn(
        SocialProvider socialProvider,
        String providerSubject,
        Collection<SocialUnlinkStatus> statuses
    );

    /** 연동 해제 작업 조회(처리 시점 도래, 재시도 시각순, 최대 100건) */
    List<SocialUnlinkTask> findTop100ByStatusInAndNextRetryAtLessThanEqualOrderByNextRetryAtAsc(
        Collection<SocialUnlinkStatus> statuses,
        LocalDateTime now
    );

    /** 연동 해제 작업 조회(제한 시간 넘게 처리 중, 오래된 순, 최대 100건) */
    List<SocialUnlinkTask> findTop100ByStatusAndUpdatedAtLessThanEqualOrderByUpdatedAtAsc(
        SocialUnlinkStatus status,
        LocalDateTime staleBefore
    );
}
