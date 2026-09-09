package com.ddip.backend.common.es.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.ddip.backend.common.dto.es.SearchAutoCompleteResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SearchAddOnService {

    private final ElasticsearchClient elasticsearchClient;

    /**
     * 검색 자동완성
     */
    public List<SearchAutoCompleteResponse> searchAutoComplete(String keyword) {
        try {
            SearchRequest searchRequest = new SearchRequest.Builder()
                    // 모든 인덱스
                    .index("*")
                    .query(q -> q
                            .matchPhrase(m -> m
                                    // keyword 와 맞는 제목 매치
                                    .query(keyword)
                                    .field("title.ngram")
                            )
                    )
                    .size(5)
                    .build();

            SearchResponse<SearchAutoCompleteResponse> searchResponse =
                    elasticsearchClient.search(searchRequest, SearchAutoCompleteResponse.class);

            return searchResponse.hits().hits().stream()
                    .map(Hit::source)
                    .toList();
        } catch (IOException e) {
            log.error("Elasticsearch 자동 완성 중 오류 발생: {}", e.getMessage());
            return Collections.emptyList();
        }
    }
}
