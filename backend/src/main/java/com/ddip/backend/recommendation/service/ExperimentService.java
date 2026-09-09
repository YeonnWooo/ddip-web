package com.ddip.backend.recommendation.service;

import com.ddip.backend.pledge.repository.PledgeRepository;
import com.ddip.backend.project.domain.Project;
import com.ddip.backend.project.dto.enums.ProjectCategory;
import com.ddip.backend.project.dto.enums.ProjectStatus;
import com.ddip.backend.project.repository.ProjectRepository;
import com.ddip.backend.recommendation.config.AhpWeightConfig;
import com.ddip.backend.recommendation.config.FuzzyAhpComputer;
import com.ddip.backend.recommendation.dto.ExperimentResponseDto;
import com.ddip.backend.recommendation.dto.ProjectCriteriaDto;
import com.ddip.backend.recommendation.dto.UserComparisonDto;
import com.ddip.backend.user.domain.User;
import com.ddip.backend.user.dto.enums.UserType;
import com.ddip.backend.user.repository.UserRepository;
import com.ddip.backend.user.validation.user.UserNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ExperimentService {

    private final ProjectRepository projectRepository;
    private final PledgeRepository pledgeRepository;
    private final UserRepository userRepository;

    // ── Fuzzy AHP 쌍대비교 행렬 ────────────────────────────────────────────
    //
    // [가치지향형] V1>V2>V3 (1-3-5 scale)
    //       V1    V2    V3
    //  V1 [  1     3     5 ]
    //  V2 [ 1/3    1     3 ]
    //  V3 [ 1/5   1/3    1 ]
    private static final double[][][] VALUE_MATRIX = {
            { FuzzyAhpComputer.tfn(1),              FuzzyAhpComputer.tfn(3),              FuzzyAhpComputer.tfn(5)  },
            { FuzzyAhpComputer.tfnReciprocal(3),    FuzzyAhpComputer.tfn(1),              FuzzyAhpComputer.tfn(3)  },
            { FuzzyAhpComputer.tfnReciprocal(5),    FuzzyAhpComputer.tfnReciprocal(3),    FuzzyAhpComputer.tfn(1)  }
    };

    // [실용지향형] P1>P2>P3 (1-3-3, 1/3-1-2, 1/3-1/2-1 scale)
    //       P1    P2    P3
    //  P1 [  1     3     3 ]
    //  P2 [ 1/3    1     2 ]
    //  P3 [ 1/3   1/2    1 ]
    private static final double[][][] PRACTICAL_MATRIX = {
            { FuzzyAhpComputer.tfn(1),              FuzzyAhpComputer.tfn(3),              FuzzyAhpComputer.tfn(3)  },
            { FuzzyAhpComputer.tfnReciprocal(3),    FuzzyAhpComputer.tfn(1),              FuzzyAhpComputer.tfn(2)  },
            { FuzzyAhpComputer.tfnReciprocal(3),    FuzzyAhpComputer.tfnReciprocal(2),    FuzzyAhpComputer.tfn(1)  }
    };

    // [트렌드지향형] T1>T2>T3 — V와 동일 구조로 T 서브 가중치 계산
    private static final double[][][] TREND_SUB_MATRIX = VALUE_MATRIX;

    private static final List<String> CRITERIA_LABELS = List.of(
            "사회적 가치", "신뢰도", "사회증명",
            "기능성", "경제적가치", "실현가능성",
            "트렌드", "달성률", "긴박성"
    );

    private static final Map<UserType, String> USER_TYPE_LABELS = Map.of(
            UserType.VALUE_ORIENTED, "가치지향형",
            UserType.PRACTICAL_ORIENTED, "실용지향형",
            UserType.TREND_ORIENTED, "트렌드지향형"
    );

    // ── 메인 메서드 ────────────────────────────────────────────────────────

    public ExperimentResponseDto compare() {

        // 1) Fuzzy AHP 가중치 계산 (Chang's extent analysis)
        double[] vFuzzy = computeFullFuzzyWeights(UserType.VALUE_ORIENTED);
        double[] pFuzzy = computeFullFuzzyWeights(UserType.PRACTICAL_ORIENTED);
        double[] tFuzzy = computeFullFuzzyWeights(UserType.TREND_ORIENTED);

        Map<UserType, double[]> fuzzyWeights = Map.of(
                UserType.VALUE_ORIENTED, vFuzzy,
                UserType.PRACTICAL_ORIENTED, pFuzzy,
                UserType.TREND_ORIENTED, tFuzzy
        );

        // 2) OPEN 프로젝트 + 기준값 수집
        List<Project> openProjects = projectRepository.findAll().stream()
                .filter(p -> p.getStatus() == ProjectStatus.OPEN)
                .toList();

        if (openProjects.isEmpty()) {
            return ExperimentResponseDto.builder()
                    .criteriaLabels(CRITERIA_LABELS)
                    .userTypes(Map.of())
                    .build();
        }

        List<PB> pbs = openProjects.stream()
                .map(p -> new PB(p, pledgeRepository.countBackersByProjectId(p.getId())))
                .toList();

        List<ProjectCriteriaDto> criteriaList = pbs.stream()
                .map(pb -> ProjectCriteriaDto.from(pb.project(), pb.backerCount()))
                .toList();

        // 3) 유저 타입별 비교 결과 생성
        Map<String, ExperimentResponseDto.UserTypeComparison> result = new LinkedHashMap<>();

        for (UserType userType : List.of(
                UserType.VALUE_ORIENTED,
                UserType.PRACTICAL_ORIENTED,
                UserType.TREND_ORIENTED)) {

            double[] ahpW   = AhpWeightConfig.getWeights(userType);
            double[] fuzzyW = fuzzyWeights.get(userType);

            double[] ahpScores   = topsis(criteriaList, ahpW);
            double[] fuzzyScores = topsis(criteriaList, fuzzyW);

            List<ExperimentResponseDto.ProjectRankEntry> ahpRank   = buildRanking(pbs, ahpScores);
            List<ExperimentResponseDto.ProjectRankEntry> fuzzyRank = buildRanking(pbs, fuzzyScores);

            double spearman = spearmanCorrelation(ahpRank, fuzzyRank);

            result.put(userType.name(), ExperimentResponseDto.UserTypeComparison.builder()
                    .label(USER_TYPE_LABELS.get(userType))
                    .ahpWeights(toList(ahpW))
                    .fuzzyAhpWeights(toList(fuzzyW))
                    .ahpRankings(ahpRank)
                    .fuzzyAhpRankings(fuzzyRank)
                    .spearmanCorrelation(spearman)
                    .build());
        }

        return ExperimentResponseDto.builder()
                .criteriaLabels(CRITERIA_LABELS)
                .userTypes(result)
                .build();
    }

    // ── Fuzzy AHP 가중치 → 9차원 전체 벡터로 확장 ─────────────────────────

    private double[] computeFullFuzzyWeights(UserType type) {
        return switch (type) {
            case VALUE_ORIENTED -> {
                double[] w3 = FuzzyAhpComputer.compute(VALUE_MATRIX);
                // [0]~[2] → V, 나머지 0
                yield new double[]{ w3[0], w3[1], w3[2], 0, 0, 0, 0, 0, 0 };
            }
            case PRACTICAL_ORIENTED -> {
                double[] w3 = FuzzyAhpComputer.compute(PRACTICAL_MATRIX);
                // [3]~[5] → P, 나머지 0
                yield new double[]{ 0, 0, 0, w3[0], w3[1], w3[2], 0, 0, 0 };
            }
            case TREND_ORIENTED -> {
                double[] vW = FuzzyAhpComputer.compute(VALUE_MATRIX);
                double[] pW = FuzzyAhpComputer.compute(PRACTICAL_MATRIX);
                double[] tW = FuzzyAhpComputer.compute(TREND_SUB_MATRIX); // T축 자체 가중치

                // 블렌드: V 25% + P 25% + T 50% (동일 blend 공식)
                yield new double[]{
                        0.25 * vW[0],            // socialValue
                        0.25 * vW[1],            // reliability
                        0.25 * vW[2],            // socialProof
                        0.25 * pW[0],            // functionality
                        0.25 * pW[1],            // economicValue
                        0.25 * pW[2],            // feasibility
                        0.50 * tW[0],            // backerCount (T1)
                        0.50 * tW[1],            // achievementRate (T2)
                        0.50 * tW[2]             // urgency (T3)
                };
            }
        };
    }

    // ── TOPSIS ────────────────────────────────────────────────────────────

    private double[] topsis(List<ProjectCriteriaDto> criteriaList, double[] weights) {
        int n = criteriaList.size(), m = weights.length;
        double[][] matrix = new double[n][m];
        for (int i = 0; i < n; i++) matrix[i] = criteriaList.get(i).toArray();

        // 벡터 정규화
        double[] colNorm = new double[m];
        for (int j = 0; j < m; j++) {
            double s = 0;
            for (int i = 0; i < n; i++) s += matrix[i][j] * matrix[i][j];
            colNorm[j] = Math.sqrt(s);
        }
        double[][] weighted = new double[n][m];
        for (int i = 0; i < n; i++)
            for (int j = 0; j < m; j++)
                weighted[i][j] = (colNorm[j] == 0 ? 0 : matrix[i][j] / colNorm[j]) * weights[j];

        // 이상해 / 이상악
        double[] ideal = new double[m], anti = new double[m];
        Arrays.fill(ideal, Double.NEGATIVE_INFINITY);
        Arrays.fill(anti,  Double.POSITIVE_INFINITY);
        for (int i = 0; i < n; i++)
            for (int j = 0; j < m; j++) {
                ideal[j] = Math.max(ideal[j], weighted[i][j]);
                anti[j]  = Math.min(anti[j],  weighted[i][j]);
            }

        // 근접도 점수
        double[] scores = new double[n];
        for (int i = 0; i < n; i++) {
            double dPlus = 0, dMinus = 0;
            for (int j = 0; j < m; j++) {
                dPlus  += Math.pow(weighted[i][j] - ideal[j], 2);
                dMinus += Math.pow(weighted[i][j] - anti[j],  2);
            }
            dPlus = Math.sqrt(dPlus);
            dMinus = Math.sqrt(dMinus);
            scores[i] = (dPlus + dMinus) == 0 ? 0 : dMinus / (dPlus + dMinus);
        }
        return scores;
    }

    // ── 랭킹 리스트 생성 ─────────────────────────────────────────────────

    private List<ExperimentResponseDto.ProjectRankEntry> buildRanking(
            List<PB> pbs, double[] scores) {

        record Idx(int i, double score) {}
        List<Idx> sorted = new ArrayList<>();
        for (int i = 0; i < scores.length; i++) sorted.add(new Idx(i, scores[i]));
        sorted.sort(Comparator.comparingDouble(Idx::score).reversed());

        List<ExperimentResponseDto.ProjectRankEntry> list = new ArrayList<>();
        for (int r = 0; r < sorted.size(); r++) {
            Idx idx = sorted.get(r);
            Project p = pbs.get(idx.i()).project();
            list.add(ExperimentResponseDto.ProjectRankEntry.builder()
                    .rank(r + 1)
                    .projectId(p.getId())
                    .title(p.getTitle())
                    .categoryPath(p.getCategoryPath())
                    .score(Math.round(idx.score() * 10000.0) / 10000.0)
                    .build());
        }
        return list;
    }

    // ── Spearman 순위 상관계수 ────────────────────────────────────────────

    private double spearmanCorrelation(
            List<ExperimentResponseDto.ProjectRankEntry> ahpRank,
            List<ExperimentResponseDto.ProjectRankEntry> fuzzyRank) {

        int n = ahpRank.size();
        if (n == 0) return 0.0;

        // projectId → rank 맵 구성
        Map<Long, Integer> ahpMap = new HashMap<>();
        for (var e : ahpRank) ahpMap.put(e.getProjectId(), e.getRank());
        Map<Long, Integer> fuzzyMap = new HashMap<>();
        for (var e : fuzzyRank) fuzzyMap.put(e.getProjectId(), e.getRank());

        // 공통 프로젝트에 대해 d^2 합산
        double dSqSum = 0;
        int count = 0;
        for (Long pid : ahpMap.keySet()) {
            if (!fuzzyMap.containsKey(pid)) continue;
            double d = ahpMap.get(pid) - fuzzyMap.get(pid);
            dSqSum += d * d;
            count++;
        }
        if (count < 2) return 0.0;
        double rho = 1.0 - (6.0 * dSqSum) / ((double) count * (count * count - 1));
        return Math.round(rho * 10000.0) / 10000.0;
    }

    private List<Double> toList(double[] arr) {
        List<Double> list = new ArrayList<>();
        for (double v : arr) list.add(Math.round(v * 10000.0) / 10000.0);
        return list;
    }

    // ── 로그인 유저 개인화 비교 ───────────────────────────────────────────────

    public UserComparisonDto myComparison(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        UserType userType = user.getUserType();
        if (userType == null) userType = UserType.TREND_ORIENTED; // 미설문 fallback

        // 카테고리 필터 (recommend와 동일)
        Set<ProjectCategory> preferred = Set.copyOf(AhpWeightConfig.getPreferredCategories(userType));

        List<Project> filtered = projectRepository.findAll().stream()
                .filter(p -> p.getStatus() == ProjectStatus.OPEN)
                .filter(p -> {
                    if (p.getCategoryPath() == null) return false;
                    try {
                        return preferred.contains(ProjectCategory.valueOf(p.getCategoryPath().toUpperCase()));
                    } catch (IllegalArgumentException e) { return false; }
                })
                .toList();

        if (filtered.isEmpty()) {
            return buildEmptyComparison(userType);
        }

        List<PB> pbs = filtered.stream()
                .map(p -> new PB(p, pledgeRepository.countBackersByProjectId(p.getId())))
                .toList();
        List<ProjectCriteriaDto> criteriaList = pbs.stream()
                .map(pb -> ProjectCriteriaDto.from(pb.project(), pb.backerCount()))
                .toList();

        double[] ahpW   = AhpWeightConfig.getWeights(userType);
        double[] fuzzyW = computeFullFuzzyWeights(userType);

        double[] ahpScores   = topsis(criteriaList, ahpW);
        double[] fuzzyScores = topsis(criteriaList, fuzzyW);

        List<UserComparisonDto.ProjectCard> ahpCards   = buildCards(pbs, ahpScores);
        List<UserComparisonDto.ProjectCard> fuzzyCards = buildCards(pbs, fuzzyScores);

        double spearman = spearmanCards(ahpCards, fuzzyCards);

        return UserComparisonDto.builder()
                .userType(userType.name())
                .userTypeLabel(USER_TYPE_LABELS.get(userType))
                .criteriaLabels(CRITERIA_LABELS)
                .ahpWeights(toList(ahpW))
                .fuzzyAhpWeights(toList(fuzzyW))
                .spearmanCorrelation(spearman)
                .ahpRankings(ahpCards)
                .fuzzyAhpRankings(fuzzyCards)
                .build();
    }

    private List<UserComparisonDto.ProjectCard> buildCards(List<PB> pbs, double[] scores) {
        record Idx(int i, double score) {}
        List<Idx> sorted = new ArrayList<>();
        for (int i = 0; i < scores.length; i++) sorted.add(new Idx(i, scores[i]));
        sorted.sort(Comparator.comparingDouble(Idx::score).reversed());

        List<UserComparisonDto.ProjectCard> cards = new ArrayList<>();
        for (int r = 0; r < sorted.size(); r++) {
            Project p = pbs.get(sorted.get(r).i()).project();
            cards.add(UserComparisonDto.ProjectCard.builder()
                    .rank(r + 1)
                    .projectId(p.getId())
                    .title(p.getTitle())
                    .summary(p.getSummary())
                    .thumbnailUrl(p.getThumbnailUrl())
                    .categoryPath(p.getCategoryPath())
                    .targetAmount(p.getTargetAmount())
                    .currentAmount(p.getCurrentAmount())
                    .backerCount(pbs.get(sorted.get(r).i()).backerCount())
                    .endAt(p.getEndAt())
                    .score(Math.round(sorted.get(r).score() * 10000.0) / 10000.0)
                    .build());
        }
        return cards;
    }

    private double spearmanCards(
            List<UserComparisonDto.ProjectCard> a,
            List<UserComparisonDto.ProjectCard> b) {
        Map<Long, Integer> aMap = new HashMap<>(), bMap = new HashMap<>();
        a.forEach(e -> aMap.put(e.getProjectId(), e.getRank()));
        b.forEach(e -> bMap.put(e.getProjectId(), e.getRank()));
        double dSq = 0; int cnt = 0;
        for (Long pid : aMap.keySet()) {
            if (!bMap.containsKey(pid)) continue;
            double d = aMap.get(pid) - bMap.get(pid);
            dSq += d * d; cnt++;
        }
        if (cnt < 2) return 0.0;
        return Math.round((1.0 - 6.0 * dSq / ((double) cnt * (cnt * cnt - 1))) * 10000.0) / 10000.0;
    }

    private UserComparisonDto buildEmptyComparison(UserType userType) {
        return UserComparisonDto.builder()
                .userType(userType.name())
                .userTypeLabel(USER_TYPE_LABELS.get(userType))
                .criteriaLabels(CRITERIA_LABELS)
                .ahpWeights(toList(AhpWeightConfig.getWeights(userType)))
                .fuzzyAhpWeights(toList(computeFullFuzzyWeights(userType)))
                .spearmanCorrelation(1.0)
                .ahpRankings(List.of())
                .fuzzyAhpRankings(List.of())
                .build();
    }

    // ── 내부 record: PB ────────────────────────────────────────────────────
    private record PB(Project project, long backerCount) {}
}
