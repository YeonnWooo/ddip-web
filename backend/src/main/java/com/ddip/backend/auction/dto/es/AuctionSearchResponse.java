package com.ddip.backend.auction.dto.es;

import com.ddip.backend.auction.domain.Auction;
import com.ddip.backend.common.es.document.AuctionDocument;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class AuctionSearchResponse {

    private Long id;

    private String title;

    private String imageKey;

    private String seller;

    private String description;

    private Long startPrice;

    private Long currentPrice;

    private String status;

    private LocalDateTime startAt;

    private LocalDateTime endAt;

    public static AuctionSearchResponse from(AuctionDocument document) {
        return AuctionSearchResponse.builder()
                .id(document.getId())
                .title(document.getTitle())
                .imageKey(document.getImageKey())
                .seller(document.getSeller())
                .description(document.getDescription())
                .startPrice(document.getStartPrice())
                .currentPrice(document.getCurrentPrice())
                .status(document.getStatus())
                .startAt(document.getStartAt())
                .endAt(document.getEndAt())
                .build();
    }
}
