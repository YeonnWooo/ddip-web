package com.ddip.backend.recommendation;

import com.ddip.backend.project.domain.Project;
import com.ddip.backend.project.domain.RewardTier;
import com.ddip.backend.project.dto.enums.ProjectStatus;
import com.ddip.backend.recommendation.dto.ProjectCriteriaDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

@DisplayName("ProjectCriteriaDto — 기준값 계산")
class ProjectCriteriaDtoTest {

    private static final double DELTA = 0.001;

    // ── 헬퍼: 기본 프로젝트 빌더 ──────────────────────────────
    private Project.ProjectBuilder baseProject() {
        return Project.builder()
                .creatorId(1L)
                .title("테스트 프로젝트")
                .targetAmount(1_000_000L)
                .currentAmount(500_000L)
                .status(ProjectStatus.OPEN)
                .likeCount(100L)
                .categoryPath("TECH")
                .endAt(LocalDate.now().plusDays(10));
    }

    private RewardTier tier(long price) {
        return RewardTier.builder()
                .title("리워드")
                .description("설명")
                .price(price)
                .soldQuantity(0L)
                .build();
    }

    // ──────────────────────────────────────────────
    // socialValue
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("socialValue — ENVIRONMENT 카테고리 점수 1.0")
    void socialValue_environment() {
        Project p = baseProject().categoryPath("ENVIRONMENT").build();
        ProjectCriteriaDto dto = ProjectCriteriaDto.of(p, 50L);
        assertThat(dto.getSocialValue()).isCloseTo(1.0, within(DELTA));
    }

    @Test
    @DisplayName("socialValue — TECH 카테고리 점수 0.6")
    void socialValue_tech() {
        Project p = baseProject().categoryPath("TECH").build();
        ProjectCriteriaDto dto = ProjectCriteriaDto.of(p, 50L);
        assertThat(dto.getSocialValue()).isCloseTo(0.6, within(DELTA));
    }

    @Test
    @DisplayName("socialValue — categoryPath가 null이면 ETC 점수(0.2) 반환")
    void socialValue_nullCategory() {
        Project p = baseProject().categoryPath(null).build();
        ProjectCriteriaDto dto = ProjectCriteriaDto.of(p, 50L);
        assertThat(dto.getSocialValue()).isCloseTo(0.2, within(DELTA));
    }

    @Test
    @DisplayName("socialValue — 알 수 없는 카테고리값이면 ETC 점수(0.2) 반환")
    void socialValue_unknownCategory() {
        Project p = baseProject().categoryPath("UNKNOWN_CATEGORY").build();
        ProjectCriteriaDto dto = ProjectCriteriaDto.of(p, 50L);
        assertThat(dto.getSocialValue()).isCloseTo(0.2, within(DELTA));
    }

    // ──────────────────────────────────────────────
    // reliability (likeCount)
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("reliability — likeCount 그대로 반영")
    void reliability_likeCount() {
        Project p = baseProject().likeCount(250L).build();
        ProjectCriteriaDto dto = ProjectCriteriaDto.of(p, 0L);
        assertThat(dto.getReliability()).isEqualTo(250.0);
    }

    // ──────────────────────────────────────────────
    // feasibility / achievementRate (달성률)
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("feasibility — currentAmount/targetAmount = 0.5 (50% 달성)")
    void feasibility_halfAchieved() {
        Project p = baseProject()
                .targetAmount(1_000_000L)
                .currentAmount(500_000L)
                .build();
        ProjectCriteriaDto dto = ProjectCriteriaDto.of(p, 0L);
        assertThat(dto.getFeasibility()).isCloseTo(0.5, within(DELTA));
        assertThat(dto.getAchievementRate()).isCloseTo(0.5, within(DELTA));
    }

    @Test
    @DisplayName("feasibility — targetAmount가 0이면 달성률 0 (ZeroDivision 방어)")
    void feasibility_zeroTarget() {
        Project p = baseProject().targetAmount(0L).currentAmount(0L).build();
        ProjectCriteriaDto dto = ProjectCriteriaDto.of(p, 0L);
        assertThat(dto.getFeasibility()).isZero();
    }

    // ──────────────────────────────────────────────
    // economicValue (최저 리워드 가격 역수)
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("economicValue — 리워드 없으면 1/1 = 1.0 (기본값)")
    void economicValue_noRewardTiers() {
        Project p = baseProject().build(); // rewardTiers 비어있음
        ProjectCriteriaDto dto = ProjectCriteriaDto.of(p, 0L);
        assertThat(dto.getEconomicValue()).isCloseTo(1.0, within(DELTA));
    }

    @Test
    @DisplayName("economicValue — 최저가 10000원이면 1/10000 = 0.0001")
    void economicValue_withRewardTiers() {
        RewardTier cheap = tier(10_000L);
        RewardTier expensive = tier(50_000L);
        Project p = baseProject().build();
        // 직접 rewardTiers 접근 불가(private) → 리플렉션 없이 빌더로 구성
        Project pWithTiers = Project.builder()
                .creatorId(1L)
                .title("리워드 있는 프로젝트")
                .targetAmount(1_000_000L)
                .currentAmount(0L)
                .status(ProjectStatus.OPEN)
                .likeCount(0L)
                .categoryPath("TECH")
                .endAt(LocalDate.now().plusDays(5))
                .rewardTiers(List.of(cheap, expensive))
                .build();

        ProjectCriteriaDto dto = ProjectCriteriaDto.of(pWithTiers, 0L);
        assertThat(dto.getEconomicValue()).isCloseTo(1.0 / 10_000, within(0.000001));
    }

    // ──────────────────────────────────────────────
    // urgency (마감 임박성)
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("urgency — 마감 10일 남으면 1/10 = 0.1")
    void urgency_tenDaysLeft() {
        Project p = baseProject().endAt(LocalDate.now().plusDays(10)).build();
        ProjectCriteriaDto dto = ProjectCriteriaDto.of(p, 0L);
        assertThat(dto.getUrgency()).isCloseTo(1.0 / 10, within(DELTA));
    }

    @Test
    @DisplayName("urgency — 마감 1일 남으면 1/1 = 1.0 (최대 긴박)")
    void urgency_oneDayLeft() {
        Project p = baseProject().endAt(LocalDate.now().plusDays(1)).build();
        ProjectCriteriaDto dto = ProjectCriteriaDto.of(p, 0L);
        assertThat(dto.getUrgency()).isCloseTo(1.0, within(DELTA));
    }

    @Test
    @DisplayName("urgency — endAt이 null이어도 NPE 없이 fallback(1.0) 반환")
    void urgency_nullEndAt_noNPE() {
        Project p = baseProject().endAt(null).build();
        assertThatCode(() -> ProjectCriteriaDto.of(p, 0L))
                .doesNotThrowAnyException();
        ProjectCriteriaDto dto = ProjectCriteriaDto.of(p, 0L);
        assertThat(dto.getUrgency()).isCloseTo(1.0, within(DELTA)); // daysLeft=1 fallback
    }

    @Test
    @DisplayName("urgency — 이미 마감된 프로젝트(과거 날짜)도 NPE 없이 fallback(1.0)")
    void urgency_pastEndAt_noNPE() {
        Project p = baseProject().endAt(LocalDate.now().minusDays(5)).build();
        assertThatCode(() -> ProjectCriteriaDto.of(p, 0L))
                .doesNotThrowAnyException();
        ProjectCriteriaDto dto = ProjectCriteriaDto.of(p, 0L);
        assertThat(dto.getUrgency()).isCloseTo(1.0, within(DELTA)); // max(음수,1) → 1
    }

    // ──────────────────────────────────────────────
    // backerCount / socialProof
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("backerCount / socialProof — 파라미터로 전달된 값 그대로 반영")
    void backerCount_reflected() {
        Project p = baseProject().build();
        ProjectCriteriaDto dto = ProjectCriteriaDto.of(p, 300L);
        assertThat(dto.getBackerCount()).isEqualTo(300.0);
        assertThat(dto.getSocialProof()).isEqualTo(300.0);
    }

    // ──────────────────────────────────────────────
    // toArray 순서 검증
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("toArray() — 9개 원소, 인덱스 순서 [socialValue..urgency]")
    void toArray_order() {
        Project p = baseProject()
                .categoryPath("ENVIRONMENT")
                .likeCount(50L)
                .targetAmount(1_000_000L)
                .currentAmount(600_000L)
                .endAt(LocalDate.now().plusDays(5))
                .build();

        ProjectCriteriaDto dto = ProjectCriteriaDto.of(p, 80L);
        double[] arr = dto.toArray();

        assertThat(arr).hasSize(9);
        assertThat(arr[0]).as("[0] socialValue").isCloseTo(1.0, within(DELTA));
        assertThat(arr[1]).as("[1] reliability").isEqualTo(50.0);
        assertThat(arr[2]).as("[2] socialProof").isEqualTo(80.0);
        assertThat(arr[5]).as("[5] feasibility").isCloseTo(0.6, within(DELTA));
        assertThat(arr[6]).as("[6] backerCount").isEqualTo(80.0);
        assertThat(arr[8]).as("[8] urgency").isCloseTo(1.0 / 5, within(DELTA));
    }
}
