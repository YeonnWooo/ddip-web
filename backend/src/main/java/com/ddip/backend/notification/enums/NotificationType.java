package com.ddip.backend.notification.enums;

public enum NotificationType {

    // ── 경매 ─────────────────────────────
    AUCTION_WIN,        // 낙찰
    AUCTION_LOST,       // 경매 패배 (최종 낙찰 실패)
    AUCTION_OUTBID,     // 입찰 추월 (선두 빼앗김)
    AUCTION_CANCELED,   // 경매 강제 취소 + 환불

    // ── 크라우드펀딩 ──────────────────────
    CROWD_SUCCESS,      // 펀딩 성공
    CROWD_FAILED,       // 펀딩 실패 + 환불

    // ── 후원 / 결제 ───────────────────────
    PLEDGE_COMPLETED,   // 후원 결제 완료
    PLEDGE_CANCELED,    // 후원 취소 + 환불

    // ── 포인트 ───────────────────────────
    POINT_CHARGED,      // 포인트 충전 완료
}
