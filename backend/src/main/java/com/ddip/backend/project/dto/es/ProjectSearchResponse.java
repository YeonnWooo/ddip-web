package com.ddip.backend.project.dto.es;

import com.ddip.backend.common.es.document.ProjectDocument;
import com.ddip.backend.project.domain.Project;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ProjectSearchResponse {

    private Long id;

    private String title;

    private String thumbnailUrl;

    private Long targetAmount;

    private Long currentAmount;

    private String status;

    private LocalDate startAt;

    private LocalDate endAt;

    public static ProjectSearchResponse from(ProjectDocument document) {
        return ProjectSearchResponse.builder()
                .id(document.getId())
                .title(document.getTitle())
                .thumbnailUrl(document.getThumbnailUrl())
                .targetAmount(document.getTargetAmount())
                .currentAmount(document.getCurrentAmount())
                .status(document.getStatus())
                .startAt(document.getStartAt())
                .endAt(document.getEndAt())
                .build();
    }
}