package com.ddip.backend.auction.event;

/**
 * 관리자 강제 취소 이벤트.
 * canceledUserId : 환불 대상 입찰자 (null = 입찰자 없음)
 */
public record AuctionCanceledEvent(Long auctionId, Long canceledUserId) {}
