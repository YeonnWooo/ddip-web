package com.ddip.backend.recommendation.dto;

import com.ddip.backend.user.dto.enums.UserType;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.util.List;

/**
 * 로그인 유저의 AHP vs Fuzzy AHP 개인화 비교 결과 DTO
 */
@Getter
@Builder
public class UserComparisonDto {

    private String userType;          // "VALUE_ORIENTED"
    private String userTypeLabel;     // "가치지향형"

    private List<String> criteriaLabels;
    private List<Double> ahpWeights;
    private List<Double> fuzzyAhpWeights;

    private double spearmanCorrelation;

    private List<ProjectCard> ahpRankings;
    private List<ProjectCard> fuzzyAhpRankings;

    @Getter
    @Builder
    public static class ProjectCard {
        private int rank;
        private Long projectId;
        private String title;
        private String summary;
        private String thumbnailUrl;
        private String categoryPath;
        private Long targetAmount;
        private Long currentAmount;
        private long backerCount;
        private LocalDate endAt;
        private double score;
    }
}
