package com.ddip.backend.billing.dto;

import com.ddip.backend.billing.domain.PointLedger;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PointHistoryResponseDto {

    private Long id;
    private Long changeAmount;
    private Long balanceAfter;
    private String type;
    private String source;
    private String description;
    private LocalDateTime createdAt;

    public static PointHistoryResponseDto from(PointLedger ledger) {
        return PointHistoryResponseDto.builder()
                .id(ledger.getId())
                .changeAmount(ledger.getChangeAmount())
                .balanceAfter(ledger.getBalanceAfter())
                .type(ledger.getType().name())
                .source(ledger.getSource().name())
                .description(ledger.getDescription())
                .createdAt(ledger.getCreateTime())
                .build();
    }
}
