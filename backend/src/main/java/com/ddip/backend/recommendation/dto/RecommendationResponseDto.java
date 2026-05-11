package com.ddip.backend.recommendation.dto;

import com.ddip.backend.project.domain.Project;
import com.ddip.backend.user.dto.enums.UserType;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;

@Getter
@Builder
public class RecommendationResponseDto {

    private Long projectId;
    private String title;
    private String summary;
    private String thumbnailUrl;
    private String categoryPath;
    private Long targetAmount;
    private Long currentAmount;
    private long backerCount;   // 후원자 수 (프론트 카드 표시용)
    private LocalDate endAt;    // 마감일 (프론트 daysLeft 계산용)
    private double score;       // TOPSIS 근접도 점수
    private UserType userType;

    public static RecommendationResponseDto of(Project project, double score, UserType userType, long backerCount) {
        return RecommendationResponseDto.builder()
                .projectId(project.getId())
                .title(project.getTitle())
                .summary(project.getSummary())
                .thumbnailUrl(project.getThumbnailUrl())
                .categoryPath(project.getCategoryPath())
                .targetAmount(project.getTargetAmount())
                .currentAmount(project.getCurrentAmount())
                .backerCount(backerCount)
                .endAt(project.getEndAt())
                .score(score)
                .userType(userType)
                .build();
    }
}
