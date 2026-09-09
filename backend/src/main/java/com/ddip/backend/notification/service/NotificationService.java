package com.ddip.backend.notification.service;

import com.ddip.backend.notification.domain.Notification;
import com.ddip.backend.notification.dto.request.NotificationRequest;
import com.ddip.backend.notification.dto.response.NotificationResponse;
import com.ddip.backend.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final SimpMessagingTemplate messagingTemplate;

    /** 알림 DB 저장 + WebSocket 발송 */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveAndSend(NotificationRequest request) {
        Notification saved = notificationRepository.save(Notification.from(request));

        messagingTemplate.convertAndSend(
                "/topic/notification/" + request.getUserId(),
                NotificationResponse.from(saved));

        log.info("[Notification] userId={} type={} referenceId={}",
                request.getUserId(), request.getType(), request.getReferenceId());
    }

    /** 내 알림 목록 (최신 50건) */
    @Transactional(readOnly = true)
    public List<NotificationResponse> getMyNotifications(Long userId) {
        return notificationRepository
                .findTop50ByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(NotificationResponse::from)
                .toList();
    }

    /** 읽지 않은 알림 수 */
    @Transactional(readOnly = true)
    public long getUnreadCount(Long userId) {
        return notificationRepository.countByUserIdAndIsReadFalse(userId);
    }

    /** 단건 읽음 처리 */
    @Transactional
    public void markRead(Long userId, Long notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new IllegalArgumentException("알림을 찾을 수 없습니다: " + notificationId));
        if (!notification.getUserId().equals(userId)) {
            throw new SecurityException("해당 알림에 접근 권한이 없습니다.");
        }
        notification.markRead();
    }

    /** 전체 읽음 처리 */
    @Transactional
    public void markAllRead(Long userId) {
        notificationRepository.markAllReadByUserId(userId);
    }
}
