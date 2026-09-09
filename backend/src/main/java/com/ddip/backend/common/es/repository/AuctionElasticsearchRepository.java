package com.ddip.backend.common.es.repository;

import com.ddip.backend.common.es.document.AuctionDocument;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AuctionElasticsearchRepository extends ElasticsearchRepository<AuctionDocument, Long> {
}
