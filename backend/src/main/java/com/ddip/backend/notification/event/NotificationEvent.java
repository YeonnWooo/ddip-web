package com.ddip.backend.notification.event;

import com.ddip.backend.notification.enums.NotificationType;

/**
 * 범용 알림 발행 이벤트.
 * <p>
 * 트랜잭션 커밋 후 {@link com.ddip.backend.common.handler.AfterCommitEventHandler#onNotificationEvent}
 * 가 처리합니다.
 */
public record NotificationEvent(
        Long userId,
        NotificationType type,
        String title,
        String message,
        Long referenceId
) {}
