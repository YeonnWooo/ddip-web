package com.ddip.backend.common.es.document;

import com.ddip.backend.auction.domain.Auction;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.data.annotation.Id;
import lombok.*;
import org.springframework.data.elasticsearch.annotations.*;

import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@Document(indexName = "auction", createIndex = true, writeTypeHint = WriteTypeHint.FALSE)
@Setting(settingPath = "elasticsearch/tokenizer-setting.json")
@Mapping(mappingPath = "elasticsearch/auction-mapping.json")
public class AuctionDocument {

    @Id
    @Field(type = FieldType.Long)
    private Long id;

    @Field(type = FieldType.Text)
    private String title;

    @Field(type = FieldType.Text)
    private String imageKey;

    @Field(type = FieldType.Text)
    private String description;

    @Field(type = FieldType.Keyword)
    private String seller;

    @Field(type = FieldType.Long)
    private Long startPrice;

    @Field(type = FieldType.Long)
    private Long currentPrice;

    @Field(type = FieldType.Keyword)
    private String status;

    @Field(type = FieldType.Date, format = {DateFormat.date_hour_minute_second_millis, DateFormat.epoch_millis})
    private LocalDateTime startAt;

    @Field(type = FieldType.Date, format = {DateFormat.date_hour_minute_second_millis, DateFormat.epoch_millis})
    private LocalDateTime endAt;

    public static AuctionDocument from(Auction auction, String imageKey) {
        return AuctionDocument.builder()
                .id(auction.getId())
                .title(auction.getTitle())
                .imageKey(imageKey)
                .description(auction.getDescription())
                .seller(auction.getSeller().getUsername())
                .startPrice(auction.getStartPrice())
                .currentPrice(auction.getCurrentPrice())
                .status(String.valueOf(auction.getAuctionStatus()))
                .startAt(auction.getStartAt())
                .endAt(auction.getEndAt())
                .build();
    }
}
