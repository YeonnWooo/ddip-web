package com.ddip.backend.recommendation;

import com.ddip.backend.project.dto.enums.ProjectCategory;
import com.ddip.backend.recommendation.config.AhpWeightConfig;
import com.ddip.backend.user.dto.enums.UserType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

@DisplayName("AhpWeightConfig — 가중치 및 선호 카테고리 매핑")
class AhpWeightConfigTest {

    private static final double DELTA = 0.001;

    // ──────────────────────────────────────────────
    // 가중치 검증
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("가치지향형 AHP 가중치 — V1=0.6334, V2=0.2605, V3=0.1062")
    void weights_valueOriented() {
        double[] w = AhpWeightConfig.getWeights(UserType.VALUE_ORIENTED);

        assertThat(w[0]).as("socialValue").isCloseTo(0.6334, within(DELTA));
        assertThat(w[1]).as("reliability").isCloseTo(0.2605, within(DELTA));
        assertThat(w[2]).as("socialProof").isCloseTo(0.1062, within(DELTA));
        // 실용/트렌드 기준은 0
        assertThat(w[3]).as("functionality — 0 이어야 함").isZero();
        assertThat(w[6]).as("backerCount — 0 이어야 함").isZero();
    }

    @Test
    @DisplayName("실용지향형 AHP 가중치 — P1=0.5889, P2=0.2519, P3=0.1593")
    void weights_practicalOriented() {
        double[] w = AhpWeightConfig.getWeights(UserType.PRACTICAL_ORIENTED);

        assertThat(w[3]).as("functionality").isCloseTo(0.5889, within(DELTA));
        assertThat(w[4]).as("economicValue").isCloseTo(0.2519, within(DELTA));
        assertThat(w[5]).as("feasibility").isCloseTo(0.1593, within(DELTA));
        // 가치/트렌드 기준은 0
        assertThat(w[0]).as("socialValue — 0 이어야 함").isZero();
        assertThat(w[6]).as("backerCount — 0 이어야 함").isZero();
    }

    @Test
    @DisplayName("트렌드지향형 AHP 가중치 — T1=0.6334, T2=0.2605, T3=0.1062")
    void weights_trendOriented() {
        double[] w = AhpWeightConfig.getWeights(UserType.TREND_ORIENTED);

        assertThat(w[6]).as("backerCount").isCloseTo(0.6334, within(DELTA));
        assertThat(w[7]).as("achievementRate").isCloseTo(0.2605, within(DELTA));
        assertThat(w[8]).as("urgency").isCloseTo(0.1062, within(DELTA));
        // 가치/실용 기준은 0
        assertThat(w[0]).as("socialValue — 0 이어야 함").isZero();
        assertThat(w[3]).as("functionality — 0 이어야 함").isZero();
    }

    @Test
    @DisplayName("각 성향별 가중치 합산이 1.0 (±0.01)")
    void weights_sumEqualsOne() {
        for (UserType type : UserType.values()) {
            double[] w = AhpWeightConfig.getWeights(type);
            double sum = 0;
            for (double v : w) sum += v;
            assertThat(sum)
                    .as("%s 가중치 합산", type)
                    .isCloseTo(1.0, within(0.01));
        }
    }

    @Test
    @DisplayName("가중치 배열 길이는 9 (9개 기준)")
    void weights_arrayLength() {
        for (UserType type : UserType.values()) {
            assertThat(AhpWeightConfig.getWeights(type)).hasSize(9);
        }
    }

    // ──────────────────────────────────────────────
    // 선호 카테고리 검증
    // ──────────────────────────────────────────────

    @Test
    @DisplayName("가치지향형 선호 카테고리 — ENVIRONMENT, SOCIAL, EDUCATION, CULTURE")
    void preferredCategories_valueOriented() {
        List<ProjectCategory> cats = AhpWeightConfig.getPreferredCategories(UserType.VALUE_ORIENTED);

        assertThat(cats).containsExactlyInAnyOrder(
                ProjectCategory.ENVIRONMENT,
                ProjectCategory.SOCIAL,
                ProjectCategory.EDUCATION,
                ProjectCategory.CULTURE
        );
    }

    @Test
    @DisplayName("실용지향형 선호 카테고리 — TECH, HEALTH, FOOD, FASHION, GAME")
    void preferredCategories_practicalOriented() {
        List<ProjectCategory> cats = AhpWeightConfig.getPreferredCategories(UserType.PRACTICAL_ORIENTED);

        assertThat(cats).containsExactlyInAnyOrder(
                ProjectCategory.TECH,
                ProjectCategory.HEALTH,
                ProjectCategory.FOOD,
                ProjectCategory.FASHION,
                ProjectCategory.GAME
        );
    }

    @Test
    @DisplayName("트렌드지향형 선호 카테고리 — null (전체 대상)")
    void preferredCategories_trendOriented_isNull() {
        List<ProjectCategory> cats = AhpWeightConfig.getPreferredCategories(UserType.TREND_ORIENTED);
        assertThat(cats).isNull();
    }
}
