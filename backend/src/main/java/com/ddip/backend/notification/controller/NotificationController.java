package com.ddip.backend.notification.controller;

import com.ddip.backend.common.security.auth.CustomUserDetails;
import com.ddip.backend.notification.dto.response.NotificationResponse;
import com.ddip.backend.notification.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/notification")
@Tag(name = "Notification", description = "알림 API")
public class NotificationController {

    private final NotificationService notificationService;

    /** 내 알림 목록 (최신 50건) */
    @GetMapping
    @Operation(summary = "알림 목록 조회")
    public ResponseEntity<List<NotificationResponse>> getNotifications(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(notificationService.getMyNotifications(userDetails.getUserId()));
    }

    /** 읽지 않은 알림 수 */
    @GetMapping("/unread-count")
    @Operation(summary = "읽지 않은 알림 수 조회")
    public ResponseEntity<Map<String, Long>> getUnreadCount(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        long count = notificationService.getUnreadCount(userDetails.getUserId());
        return ResponseEntity.ok(Map.of("count", count));
    }

    /** 단건 읽음 처리 */
    @PatchMapping("/{notificationId}/read")
    @Operation(summary = "알림 읽음 처리")
    public ResponseEntity<Void> markRead(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long notificationId) {
        notificationService.markRead(userDetails.getUserId(), notificationId);
        return ResponseEntity.noContent().build();
    }

    /** 전체 읽음 처리 */
    @PatchMapping("/read-all")
    @Operation(summary = "알림 전체 읽음 처리")
    public ResponseEntity<Void> markAllRead(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        notificationService.markAllRead(userDetails.getUserId());
        return ResponseEntity.noContent().build();
    }
}
