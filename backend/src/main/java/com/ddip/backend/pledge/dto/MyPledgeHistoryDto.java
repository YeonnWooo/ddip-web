package com.ddip.backend.pledge.dto;

import com.ddip.backend.pledge.domain.Pledge;
import com.ddip.backend.pledge.dto.enums.PledgeStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 마이페이지 후원 내역 응답 DTO
 * - 주문(orderId) 단위로 묶어서 반환
 * - projectTitle, rewardTierTitle 포함
 */
@Getter
@Builder
public class MyPledgeHistoryDto {

    private Long projectId;
    private String projectTitle;       // 프로젝트 이름
    private String orderId;
    private Long totalAmount;
    private LocalDateTime createdAt;
    private List<PledgeItemDto> items;

    @Getter
    @Builder
    public static class PledgeItemDto {
        private Long pledgeId;
        private Long rewardTierId;
        private String rewardTierTitle; // 리워드 이름
        private Long quantity;
        private Long amount;
        private PledgeStatus status;
    }
}
