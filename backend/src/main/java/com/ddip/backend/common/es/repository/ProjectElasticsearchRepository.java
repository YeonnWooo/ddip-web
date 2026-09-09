package com.ddip.backend.common.es.repository;

import com.ddip.backend.common.es.document.ProjectDocument;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProjectElasticsearchRepository extends ElasticsearchRepository<ProjectDocument, Long> {
}