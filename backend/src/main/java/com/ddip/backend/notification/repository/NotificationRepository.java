package com.ddip.backend.notification.repository;

import com.ddip.backend.notification.domain.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    /** 최신 순 최대 50건 조회 */
    List<Notification> findTop50ByUserIdOrderByCreatedAtDesc(Long userId);

    /** 읽지 않은 알림 수 */
    long countByUserIdAndIsReadFalse(Long userId);

    /** 특정 유저 전체 읽음 처리 */
    @Modifying
    @Query("update Notification n set n.isRead = true where n.userId = :userId and n.isRead = false")
    int markAllReadByUserId(@Param("userId") Long userId);
}
