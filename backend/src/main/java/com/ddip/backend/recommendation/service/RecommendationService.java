package com.ddip.backend.recommendation.service;

import com.ddip.backend.pledge.repository.PledgeRepository;
import com.ddip.backend.project.domain.Project;
import com.ddip.backend.project.dto.enums.ProjectCategory;
import com.ddip.backend.project.dto.enums.ProjectStatus;
import com.ddip.backend.project.repository.ProjectRepository;
import com.ddip.backend.recommendation.config.AhpWeightConfig;
import com.ddip.backend.recommendation.dto.ProjectCriteriaDto;
import com.ddip.backend.recommendation.dto.RecommendationResponseDto;
import com.ddip.backend.user.domain.User;
import com.ddip.backend.user.dto.enums.UserType;
import com.ddip.backend.user.repository.UserRepository;
import com.ddip.backend.user.validation.user.UserNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class RecommendationService {

    private static final int TOP_N = 10;

    private final ProjectRepository projectRepository;
    private final PledgeRepository pledgeRepository;
    private final UserRepository userRepository;

    public List<RecommendationResponseDto> recommend(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        // 설문 미완료 시 최신순 반환 (createTime null 방어 — nullsLast)
        if (user.getUserType() == null) {
            log.info("설문 미완료 유저 — 최신순 반환: userId={}", userId);
            return projectRepository.findAll().stream()
                    .filter(p -> p.getStatus() == ProjectStatus.OPEN)
                    .sorted(Comparator.comparing(
                            Project::getCreateTime,
                            Comparator.nullsLast(Comparator.naturalOrder())
                    ).reversed())
                    .limit(TOP_N)
                    .map(p -> RecommendationResponseDto.of(
                            p, 0.0, null,
                            pledgeRepository.countBackersByProjectId(p.getId())
                    ))
                    .toList();
        }

        UserType userType = user.getUserType();
        double[] weights = AhpWeightConfig.getWeights(userType);

        // 1) OPEN 프로젝트 조회
        List<Project> allOpenProjects = projectRepository.findAll().stream()
                .filter(p -> p.getStatus() == ProjectStatus.OPEN)
                .toList();

        if (allOpenProjects.isEmpty()) return List.of();

        // 2) 성향별 카테고리 필터링
        //    - VALUE_ORIENTED    : ENVIRONMENT, SOCIAL, EDUCATION, CULTURE
        //    - PRACTICAL_ORIENTED: TECH, HEALTH, FOOD, FASHION, GAME
        //    - TREND_ORIENTED    : 카테고리 무관 (전체 대상, 트렌드 지표로만 정렬)
        List<ProjectCategory> preferred = AhpWeightConfig.getPreferredCategories(userType);
        List<Project> openProjects;
        if (preferred != null && !preferred.isEmpty()) {
            Set<ProjectCategory> preferredSet = Set.copyOf(preferred);
            openProjects = allOpenProjects.stream()
                    .filter(p -> {
                        if (p.getCategoryPath() == null) return false;
                        try {
                            ProjectCategory cat = ProjectCategory.valueOf(p.getCategoryPath().toUpperCase());
                            return preferredSet.contains(cat);
                        } catch (IllegalArgumentException e) {
                            // 알 수 없는 카테고리값이 DB에 있으면 제외
                            log.warn("알 수 없는 categoryPath 값: projectId={}, categoryPath={}", p.getId(), p.getCategoryPath());
                            return false;
                        }
                    })
                    .toList();
            // 선호 카테고리 프로젝트가 없으면 빈 리스트 반환 (전체 혼합 방지)
            if (openProjects.isEmpty()) return List.of();
        } else {
            // TREND_ORIENTED: 카테고리 필터 없이 전체 대상
            openProjects = allOpenProjects;
        }

        // 3) 프로젝트별 기준값 수집 (backerCount 재사용을 위해 함께 보관)
        record ProjectWithBacker(Project project, long backerCount) {}
        List<ProjectWithBacker> projectWithBackers = openProjects.stream()
                .map(p -> new ProjectWithBacker(p, pledgeRepository.countBackersByProjectId(p.getId())))
                .toList();

        List<ProjectCriteriaDto> criteriaList = projectWithBackers.stream()
                .map(pb -> ProjectCriteriaDto.from(pb.project(), pb.backerCount()))
                .toList();

        // 4) TOPSIS 계산
        double[] scores = topsis(criteriaList, weights);

        // 5) 점수 내림차순 정렬 후 상위 N개 반환
        record Indexed(int i, double score) {}
        List<Indexed> ranked = new java.util.ArrayList<>();
        for (int i = 0; i < scores.length; i++) ranked.add(new Indexed(i, scores[i]));
        ranked.sort(Comparator.comparingDouble(Indexed::score).reversed());

        return ranked.stream()
                .limit(TOP_N)
                .map(r -> {
                    ProjectWithBacker pb = projectWithBackers.get(r.i());
                    return RecommendationResponseDto.of(pb.project(), r.score(), userType, pb.backerCount());
                })
                .toList();
    }

    // ───────────────────────────────────────────────────
    // TOPSIS 구현
    // ───────────────────────────────────────────────────
    private double[] topsis(List<ProjectCriteriaDto> criteriaList, double[] weights) {
        int n = criteriaList.size();
        int m = weights.length;

        double[][] matrix = new double[n][m];
        for (int i = 0; i < n; i++) {
            matrix[i] = criteriaList.get(i).toArray();
        }

        // Step 1: 벡터 정규화
        double[] colNorm = new double[m];
        for (int j = 0; j < m; j++) {
            double sumSq = 0;
            for (int i = 0; i < n; i++) sumSq += matrix[i][j] * matrix[i][j];
            colNorm[j] = Math.sqrt(sumSq);
        }

        double[][] normalized = new double[n][m];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < m; j++) {
                normalized[i][j] = colNorm[j] == 0 ? 0 : matrix[i][j] / colNorm[j];
            }
        }

        // Step 2: 가중치 적용
        double[][] weighted = new double[n][m];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < m; j++) {
                weighted[i][j] = normalized[i][j] * weights[j];
            }
        }

        // Step 3: 이상해(최고) / 이상악(최저) 계산
        double[] ideal      = new double[m];
        double[] antiIdeal  = new double[m];
        for (int j = 0; j < m; j++) {
            ideal[j]     = Double.NEGATIVE_INFINITY;
            antiIdeal[j] = Double.POSITIVE_INFINITY;
            for (int i = 0; i < n; i++) {
                ideal[j]     = Math.max(ideal[j],     weighted[i][j]);
                antiIdeal[j] = Math.min(antiIdeal[j], weighted[i][j]);
            }
        }

        // Step 4: 각 프로젝트 근접도 점수 계산
        double[] scores = new double[n];
        for (int i = 0; i < n; i++) {
            double dPlus = 0, dMinus = 0;
            for (int j = 0; j < m; j++) {
                dPlus  += Math.pow(weighted[i][j] - ideal[j],     2);
                dMinus += Math.pow(weighted[i][j] - antiIdeal[j], 2);
            }
            dPlus  = Math.sqrt(dPlus);
            dMinus = Math.sqrt(dMinus);
            scores[i] = (dPlus + dMinus) == 0 ? 0 : dMinus / (dPlus + dMinus);
        }

        return scores;
    }
}
