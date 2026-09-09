package com.ddip.backend.notification.dto.request;

import com.ddip.backend.notification.enums.NotificationType;
import com.ddip.backend.notification.event.NotificationEvent;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class NotificationRequest {

    private final Long userId;
    private final NotificationType type;
    private final String title;
    private final String message;
    private final Long referenceId;

    /** Spring 이벤트로부터 변환 */
    public static NotificationRequest from(NotificationEvent event) {
        return NotificationRequest.builder()
                .userId(event.userId())
                .type(event.type())
                .title(event.title())
                .message(event.message())
                .referenceId(event.referenceId())
                .build();
    }
}
