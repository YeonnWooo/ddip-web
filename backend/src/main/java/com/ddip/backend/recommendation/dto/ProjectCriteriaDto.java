package com.ddip.backend.recommendation.dto;

import com.ddip.backend.project.domain.Project;
import com.ddip.backend.project.dto.enums.ProjectCategory;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;

@Getter
@Builder
public class ProjectCriteriaDto {

    private Long projectId;

    // [가치지향형]
    private double socialValue;     // categoryPath 기반 점수
    private double reliability;     // likeCount 정규화
    private double socialProof;     // 후원자 수 정규화

    // [실용지향형]
    private double functionality;   // 리워드 종류 수 정규화
    private double economicValue;   // 최저 리워드 가격 (낮을수록 좋음 → 역수 적용)
    private double feasibility;     // 달성률

    // [트렌드지향형]
    private double backerCount;     // 후원자 수 정규화
    private double achievementRate; // 달성률
    private double urgency;         // 마감 임박성 (1 / daysLeft)

    public double[] toArray() {
        return new double[]{
                socialValue, reliability, socialProof,
                functionality, economicValue, feasibility,
                backerCount, achievementRate, urgency
        };
    }

    public static ProjectCriteriaDto of(Project project, long backerCount) {
        // endAt이 null이거나 이미 지난 경우 방어 처리 (urgency = 최솟값)
        long daysLeft = 1L;
        if (project.getEndAt() != null) {
            long remaining = LocalDate.now().until(project.getEndAt()).getDays();
            daysLeft = Math.max(remaining, 1);
        }
        double achievement = project.getTargetAmount() == 0 ? 0
                : (double) project.getCurrentAmount() / project.getTargetAmount();
        long minPrice = project.getRewardTiers().stream()
                .mapToLong(r -> r.getPrice())
                .min().orElse(1L);

        return ProjectCriteriaDto.builder()
                .projectId(project.getId())
                .socialValue(ProjectCategory.getSocialValueScore(project.getCategoryPath()))
                .reliability(project.getLikeCount())
                .socialProof(backerCount)
                .functionality(project.getRewardTiers().size())
                .economicValue(1.0 / Math.max(minPrice, 1))   // 낮은 가격 = 높은 점수
                .feasibility(achievement)
                .backerCount(backerCount)
                .achievementRate(achievement)
                .urgency(1.0 / daysLeft)                       // 마감 임박할수록 높음
                .build();
    }
}
