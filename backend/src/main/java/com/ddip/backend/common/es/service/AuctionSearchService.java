package com.ddip.backend.common.es.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.json.JsonpMappingException;
import co.elastic.clients.transport.TransportException;
import com.ddip.backend.auction.dto.es.AuctionSearchResponse;
import com.ddip.backend.common.es.document.AuctionDocument;
import com.ddip.backend.common.es.util.BuildSearchQueryUtil;
import com.ddip.backend.common.exception.es.SearchResponseNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class AuctionSearchService {

    private final ElasticsearchClient elasticsearchClient;
    private final BuildSearchQueryUtil buildSearchQueryUtil;

    /**
     * 일반 검색
     */
    public List<AuctionSearchResponse> searchAuctionsByKeyword(String title) {

        try {
            SearchRequest searchRequest = new SearchRequest.Builder()
                    // auction 인덱스
                    .index("auction")
                    .query(q -> q
                            .multiMatch(m -> m
                                    // 제목으로 검색
                                    .query(title)
                                    // 제목과 관련된 내용도 같이 검색(제목이 우선)
                                    .fields("title^2", "description")
                            )
                    )
                    .size(20)
                    .build();

            SearchResponse<AuctionDocument> searchResponse =
                    elasticsearchClient.search(searchRequest, AuctionDocument.class);

            return searchResponse.hits().hits().stream()
                    .map(Hit::source)
                    .filter(Objects::nonNull)
                    .map(AuctionSearchResponse::from)
                    .toList();

        } catch (Exception e) {
            Throwable root = e;
            while (root.getCause() != null) root = root.getCause();

            log.info("ES search failed: {}", e.toString(), e);
            log.info("ROOT CAUSE: {}: {}", root.getClass().getName(), root.getMessage());

            // 디코딩/매핑 예외면 메시지가 제일 중요합니다.
            if (root instanceof JsonpMappingException) {
                log.info("JsonpMappingException detail: {}", root.getMessage());
            }

            // transport 레벨 예외면 더 감싸져 올라오는 경우가 많습니다.
            if (e instanceof TransportException) {
                log.info("TransportException: {}", e.getMessage());
            }

            throw new SearchResponseNotFoundException("node: http://elasticsearch:9200/, status: 200, [es/search] Failed to decode response");
        }
    }

    /**
     * 상세 검색
     */
    public Page<AuctionSearchResponse> searchAuctionByFilter(String title, LocalDateTime endAt,
                                                             int page, int size) {
        Pageable pageable = PageRequest.of(page, size);

        try {
            Query query = buildSearchQueryUtil.buildAuciotnSearchQuery(title, endAt);

            SearchRequest searchRequest = SearchRequest.of(s -> s
                    .index("auction")
                    .query(query)
                    .from(page * size)
                    .size(size)
            );

            SearchResponse<AuctionDocument> searchResponse =
                    elasticsearchClient.search(searchRequest, AuctionDocument.class);

            List<AuctionSearchResponse> auctions = searchResponse.hits().hits().stream()
                    .map(Hit::source)
                    .filter(Objects::nonNull)
                    .map(AuctionSearchResponse::from)
                    .toList();

            long total = searchResponse.hits().total() == null ? auctions.size() :
                    searchResponse.hits().total().value();

            return new PageImpl<>(auctions, pageable, total);

        } catch (IOException e) {
            throw new SearchResponseNotFoundException("Elasticsearch 통신 오류");
        }
    }
}