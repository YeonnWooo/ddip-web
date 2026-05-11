package com.ddip.backend.recommendation.config;

import com.ddip.backend.project.dto.enums.ProjectCategory;
import com.ddip.backend.user.dto.enums.UserType;

import java.util.List;
import java.util.Map;

/**
 * 성향별 AHP 쌍대비교 행렬 기반 가중치 테이블
 *
 * ─────────────────────────────────────────────────────────────────
 * 기준 인덱스 (공통)
 *   0: socialValue     — 사회적 가치/윤리성  (V1)
 *   1: reliability     — 신뢰성/정당성       (V2)
 *   2: socialProof     — 사회적 증거         (V3)
 *   3: functionality   — 기능성/품질         (P1)
 *   4: economicValue   — 경제적 가치         (P2)
 *   5: feasibility     — 실행 가능성         (P3)
 *   6: backerCount     — 인기/후원자 반응    (T1)
 *   7: achievementRate — 목표 달성률         (T2)
 *   8: urgency         — 마감 임박성         (T3)
 *
 * ─────────────────────────────────────────────────────────────────
 * [가치지향형] 쌍대비교 행렬 (CR = 0.033 < 0.1 ✓)
 *        V1    V2    V3
 *   V1 [  1    3     5  ]
 *   V2 [ 1/3   1     3  ]
 *   V3 [ 1/5  1/3    1  ]
 *   → W = [0.6334, 0.2605, 0.1062]
 *
 * [실용지향형] 쌍대비교 행렬 (CR = 0.047 < 0.1 ✓)
 *        P1    P2    P3
 *   P1 [  1    3     3  ]
 *   P2 [ 1/3   1     2  ]
 *   P3 [ 1/3  1/2    1  ]
 *   → W = [0.5889, 0.2519, 0.1593]
 *
 * [트렌드지향형] 쌍대비교 행렬 (CR = 0.033 < 0.1 ✓)
 *        T1    T2    T3
 *   T1 [  1    3     5  ]
 *   T2 [ 1/3   1     3  ]
 *   T3 [ 1/5  1/3    1  ]
 *   → W = [0.6334, 0.2605, 0.1062]
 * ─────────────────────────────────────────────────────────────────
 */
public class AhpWeightConfig {

    private AhpWeightConfig() {}

    private static final Map<UserType, double[]> WEIGHTS = Map.of(
            // [0]socialValue [1]reliability [2]socialProof [3]functionality [4]economicValue [5]feasibility [6]backerCount [7]achievementRate [8]urgency
            UserType.VALUE_ORIENTED,     new double[]{0.6334, 0.2605, 0.1062, 0.0000, 0.0000, 0.0000, 0.0000, 0.0000, 0.0000},
            UserType.PRACTICAL_ORIENTED, new double[]{0.0000, 0.0000, 0.0000, 0.5889, 0.2519, 0.1593, 0.0000, 0.0000, 0.0000},
            UserType.TREND_ORIENTED,     new double[]{0.0000, 0.0000, 0.0000, 0.0000, 0.0000, 0.0000, 0.6334, 0.2605, 0.1062}
    );

    /**
     * 성향별 선호 카테고리 목록
     * - VALUE_ORIENTED    : 사회적 가치가 높은 카테고리
     * - PRACTICAL_ORIENTED: 실용적/기능적 카테고리
     * - TREND_ORIENTED    : null → 카테고리 필터 없이 전체 대상으로 트렌드 정렬
     */
    private static final Map<UserType, List<ProjectCategory>> PREFERRED_CATEGORIES = Map.of(
            UserType.VALUE_ORIENTED,     List.of(
                    ProjectCategory.ENVIRONMENT,
                    ProjectCategory.SOCIAL,
                    ProjectCategory.EDUCATION,
                    ProjectCategory.CULTURE
            ),
            UserType.PRACTICAL_ORIENTED, List.of(
                    ProjectCategory.TECH,
                    ProjectCategory.HEALTH,
                    ProjectCategory.FOOD,
                    ProjectCategory.FASHION,
                    ProjectCategory.GAME   // 게임/취미 → 실용적 소비 성향에 포함
            )
            // TREND_ORIENTED → PREFERRED_CATEGORIES에 없음 → null 반환 → 전체 카테고리 대상
            // ETC는 분류 불명확하므로 TREND_ORIENTED(전체)에서만 노출
    );

    public static double[] getWeights(UserType userType) {
        return WEIGHTS.getOrDefault(userType, WEIGHTS.get(UserType.TREND_ORIENTED));
    }

    /**
     * 성향에 해당하는 선호 카테고리 목록 반환.
     * TREND_ORIENTED 는 카테고리 무관(전체 대상)이므로 null 반환.
     */
    public static List<ProjectCategory> getPreferredCategories(UserType userType) {
        return PREFERRED_CATEGORIES.get(userType); // TREND_ORIENTED → null
    }
}
