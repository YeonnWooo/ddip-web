package com.ddip.backend.common.es.document;

import com.ddip.backend.project.domain.Project;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.*;

import java.time.LocalDate;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@Document(indexName = "project", createIndex = true, writeTypeHint = WriteTypeHint.FALSE)
@Setting(settingPath = "elasticsearch/tokenizer-setting.json")
@Mapping(mappingPath = "elasticsearch/project-mapping.json")
public class ProjectDocument {

    @Id
    @Field(type = FieldType.Long)
    private Long id;

    @Field(type = FieldType.Text)
    private String title;

    @Field(type = FieldType.Keyword, index = false)
    private String thumbnailUrl;

    @Field(type = FieldType.Long)
    private Long targetAmount;

    @Field(type = FieldType.Long)
    private Long currentAmount;

    @Field(type = FieldType.Keyword)
    private String status;

    @Field(type = FieldType.Date)
    private LocalDate startAt;

    @Field(type = FieldType.Date)
    private LocalDate endAt;

    @Field(type = FieldType.Long)
    private Long remainingDays;

    public static ProjectDocument from(Project project, String thumbnailUrl) {
        return ProjectDocument.builder()
                .id(project.getId())
                .title(project.getTitle())
                .thumbnailUrl(thumbnailUrl)
                .targetAmount(project.getTargetAmount())
                .currentAmount(project.getCurrentAmount())
                .status(String.valueOf(project.getStatus()))
                .startAt(project.getStartAt())
                .endAt(project.getEndAt())
                .build();
    }
}