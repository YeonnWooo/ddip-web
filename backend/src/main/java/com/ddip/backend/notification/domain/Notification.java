package com.ddip.backend.notification.domain;

import com.ddip.backend.notification.dto.request.NotificationRequest;
import com.ddip.backend.notification.enums.NotificationType;
import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor
@EntityListeners(AuditingEntityListener.class)
@Table(name = "notification",
        indexes = {
            @Index(name = "idx_notification_user_read", columnList = "user_id, is_read"),
            @Index(name = "idx_notification_user_created", columnList = "user_id, created_at DESC")
        }
)
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 30)
    private NotificationType type;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "message", nullable = false)
    private String message;

    @Column(name = "reference_id")
    private Long referenceId;

    @Column(name = "is_read", nullable = false)
    private boolean isRead = false;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public Notification(Long userId, String title, NotificationType type,
                        String message, Long referenceId, boolean isRead) {
        this.userId = userId;
        this.title = title;
        this.type = type;
        this.message = message;
        this.referenceId = referenceId;
        this.isRead = isRead;
    }

    // ── 비즈니스 로직 ──────────────────────────────────────────────────────────

    /** 알림 생성 팩토리 — Request DTO를 받아 엔티티로 변환 */
    public static Notification from(NotificationRequest request) {
        return Notification.builder()
                .userId(request.getUserId())
                .type(request.getType())
                .title(request.getTitle())
                .message(request.getMessage())
                .referenceId(request.getReferenceId())
                .build();
    }

    /** 읽음 처리 */
    public void markRead() {
        this.isRead = true;
    }
}
