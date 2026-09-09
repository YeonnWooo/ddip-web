package com.ddip.backend.common.es.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.json.JsonpMappingException;
import co.elastic.clients.transport.TransportException;
import com.ddip.backend.common.es.document.ProjectDocument;
import com.ddip.backend.common.es.util.BuildSearchQueryUtil;
import com.ddip.backend.common.exception.es.SearchResponseNotFoundException;
import com.ddip.backend.project.dto.es.ProjectSearchResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class ProjectSearchService {

    private final ElasticsearchClient elasticsearchClient;
    private final BuildSearchQueryUtil buildSearchQueryUtil;

    /**
     * 일반 검색
     */
    public List<ProjectSearchResponse> searchProjectByKeyword(String title) {
        try{
            SearchRequest searchRequest = new SearchRequest.Builder()
                    .index("project")
                    .query(q -> q
                            .matchPhrase(m -> m
                                    .query(title)
                                    .field("title")
                            )
                    )
                    .size(20)
                    .build();

            SearchResponse<ProjectDocument> searchResponse =
                    elasticsearchClient.search(searchRequest, ProjectDocument.class);

            return searchResponse.hits().hits().stream()
                    .map(Hit::source)
                    .filter(Objects::nonNull)
                    .map(ProjectSearchResponse::from)
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
    public Page<ProjectSearchResponse> searchProjectByFilter(String title, LocalDate endAt,
                                                             int page, int size) {
        Pageable pageable = PageRequest.of(page, size);

        try {
            Query query = buildSearchQueryUtil.buildProjectSearchQuery(title, endAt);

            SearchRequest searchRequest = SearchRequest.of(s -> s
                    .index("project")
                    .query(query)
                    .from(page * size)
                    .size(size)
            );

            SearchResponse<ProjectDocument> searchResponse =
                    elasticsearchClient.search(searchRequest, ProjectDocument.class);

            List<ProjectSearchResponse> project = searchResponse.hits().hits().stream()
                    .map(Hit::source)
                    .filter(Objects::nonNull)
                    .map(ProjectSearchResponse::from)
                    .toList();

            long total = searchResponse.hits().total() == null ? project.size() :
                    searchResponse.hits().total().value();

            return new PageImpl<>(project, pageable, total);

        } catch (IOException e) {
            throw new SearchResponseNotFoundException("Elasticsearch 통신 오류");
        }
    }
}
