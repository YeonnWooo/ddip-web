package com.ddip.backend.recommendation.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.Map;

/**
 * AHP vs Fuzzy AHP 실험 비교 응답 DTO
 */
@Getter
@Builder
public class ExperimentResponseDto {

    /** 기준명 라벨 (9개) */
    private List<String> criteriaLabels;

    /** 유저 타입별 비교 결과 */
    private Map<String, UserTypeComparison> userTypes;

    @Getter
    @Builder
    public static class UserTypeComparison {
        private String label;                        // "가치지향형" 등

        // ── 가중치 벡터 ──
        private List<Double> ahpWeights;
        private List<Double> fuzzyAhpWeights;

        // ── TOPSIS 랭킹 ──
        private List<ProjectRankEntry> ahpRankings;
        private List<ProjectRankEntry> fuzzyAhpRankings;

        // ── 켄달/스피어만 순위 상관계수 ──
        private double spearmanCorrelation;
    }

    @Getter
    @Builder
    public static class ProjectRankEntry {
        private int rank;
        private Long projectId;
        private String title;
        private String categoryPath;
        private double score;
    }
}
