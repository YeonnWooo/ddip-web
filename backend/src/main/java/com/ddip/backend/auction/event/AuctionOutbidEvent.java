package com.ddip.backend.auction.event;

/**
 * 입찰 추월 이벤트 — 선두를 빼앗긴 유저에게 알림.
 * outbidUserId : 추월당한 유저 ID
 * auctionTitle : 경매 제목 (알림 메시지용)
 * newPrice     : 새 입찰가
 */
public record AuctionOutbidEvent(Long auctionId, Long outbidUserId,
                                 String auctionTitle, Long newPrice) {}
