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

public interface SocialUnlinkTaskRepository extends JpaRepository<SocialUnlinkTask, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select task
        from SocialUnlinkTask task
        where task.id = :taskId
        """)
    Optional<SocialUnlinkTask> findByIdForUpdate(
        @Param("taskId") Long taskId
    );

    /**
     * 탈퇴 직후 생성된 작업 처리
     * @param taskIds
     * @return
     */
    List<SocialUnlinkTask> findAllByIdIn(Collection<Long> taskIds);

    /**
     * 연동 해제 중인 소셜 계정 재가입 차단
     * @param socialProvider
     * @param providerSubject
     * @param statuses
     * @return
     */
    boolean existsBySocialProviderAndProviderSubjectAndStatusIn(
        SocialProvider socialProvider,
        String providerSubject,
        Collection<SocialUnlinkStatus> statuses
    );

    /**
     * 재시도할 작업 100개씩 조회
     * @param statuses
     * @param now
     * @return
     */
    List<SocialUnlinkTask>
    findTop100ByStatusInAndNextRetryAtLessThanEqualOrderByNextRetryAtAsc(
        Collection<SocialUnlinkStatus> statuses,
        LocalDateTime now
    );

    /**
     * 제한 시간보다 오래 PROCESSING 상태에 머문 작업 조회
     * @param status
     * @param staleBefore
     * @return
     */
    List<SocialUnlinkTask>
    findTop100ByStatusAndUpdatedAtLessThanEqualOrderByUpdatedAtAsc(
        SocialUnlinkStatus status,
        LocalDateTime staleBefore
    );

}
