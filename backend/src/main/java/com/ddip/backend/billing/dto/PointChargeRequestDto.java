package com.ddip.backend.billing.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class PointChargeRequestDto {

    @NotNull
    @Min(value = 1000, message = "최소 충전 금액은 1,000원입니다.")
    private Long amount;
}
