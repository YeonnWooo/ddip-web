package com.ddip.backend.recommendation;

import com.ddip.backend.common.dto.enums.AuthProvider;
import com.ddip.backend.common.dto.enums.Role;
import com.ddip.backend.pledge.repository.PledgeRepository;
import com.ddip.backend.project.domain.Project;
import com.ddip.backend.project.dto.enums.ProjectStatus;
import com.ddip.backend.project.repository.ProjectRepository;
import com.ddip.backend.recommendation.dto.RecommendationResponseDto;
import com.ddip.backend.recommendation.service.RecommendationService;
import com.ddip.backend.user.domain.User;
import com.ddip.backend.user.dto.enums.UserType;
import com.ddip.backend.user.repository.UserRepository;
import com.ddip.backend.user.validation.user.UserNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("RecommendationService — 성향별 추천 로직")
class RecommendationServiceTest {

    @InjectMocks
    private RecommendationService recommendationService;

    @Mock private ProjectRepository projectRepository;
    @Mock private PledgeRepository pledgeRepository;
    @Mock private UserRepository userRepository;

    private static final Long USER_ID = 1L;

    // ── 헬퍼: User 픽스처 ─────────────────────────────────────
    private User userWithType(UserType type) {
        return User.builder()
                .email("test@ddip.com")
                .username("테스터")
                .nickname("테스터닉")
                .phoneNumber("010-0000-0000")
                .provider(AuthProvider.LOCAL)
                .role(Role.USER)
                .userType(type)
                .build();
    }

    private User userWithNoSurvey() {
        return User.builder()
                .email("nosurvey@ddip.com")
                .username("미설정유저")
                .nickname("미설정")
                .phoneNumber("010-1111-1111")
                .provider(AuthProvider.LOCAL)
                .role(Role.USER)
                // userType 미설정 → null
                .build();
    }

    // ── 헬퍼: Project 픽스처 ──────────────────────────────────
    private Project openProject(Long id, String category, long current, long target, long likeCount) {
        return Project.builder()
                .id(id)          // mock 매칭용 ID 명시
                .creatorId(99L)
                .title("프로젝트-" + id)
                .categoryPath(category)
                .status(ProjectStatus.OPEN)
                .currentAmount(current)
                .targetAmount(target)
                .likeCount(likeCount)
                .endAt(LocalDate.now().plusDays(10))
                .build();
    }

    private Project draftProject(String category) {
        return Project.builder()
                .id(999L)
                .creatorId(99L)
                .title("드래프트 프로젝트")
                .categoryPath(category)
                .status(ProjectStatus.DRAFT)
                .currentAmount(0L)
                .targetAmount(1_000_000L)
                .likeCount(0L)
                .endAt(LocalDate.now().plusDays(10))
                .build();
    }

    // ──────────────────────────────────────────────────────────
    // 공통 예외 케이스
    // ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("존재하지 않는 userId → UserNotFoundException")
    void recommend_userNotFound() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> recommendationService.recommend(USER_ID))
                .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    @DisplayName("설문 미완료 유저 → 최신순 OPEN 프로젝트 반환 (userType = null)")
    void recommend_noSurvey_returnsLatestOpen() {
        User user = userWithNoSurvey();
        Project p1 = openProject(1L, "TECH", 100_000L, 1_000_000L, 10L);
        Project p2 = openProject(2L, "ENVIRONMENT", 200_000L, 1_000_000L, 20L);
        Project draft = draftProject("SOCIAL");

        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(projectRepository.findAll()).thenReturn(List.of(p1, p2, draft));

        List<RecommendationResponseDto> result = recommendationService.recommend(USER_ID);

        // DRAFT는 제외, OPEN 2개만 반환
        assertThat(result).hasSize(2);
        // userType은 null (추천 기반 X)
        assertThat(result).allMatch(r -> r.getUserType() == null);
    }

    // ──────────────────────────────────────────────────────────
    // 가치지향형 (VALUE_ORIENTED)
    // ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("가치지향형 — ENVIRONMENT, SOCIAL, EDUCATION, CULTURE만 추천")
    void recommend_valueOriented_onlyPreferredCategories() {
        User user = userWithType(UserType.VALUE_ORIENTED);

        Project env  = openProject(1L, "ENVIRONMENT", 500_000L, 1_000_000L, 200L);
        Project soc  = openProject(2L, "SOCIAL",      300_000L, 1_000_000L, 100L);
        Project tech = openProject(3L, "TECH",        900_000L, 1_000_000L, 500L); // 제외
        Project game = openProject(4L, "GAME",        100_000L, 1_000_000L, 50L);  // 제외

        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(projectRepository.findAll()).thenReturn(List.of(env, soc, tech, game));
        when(pledgeRepository.countBackersByProjectId(any())).thenReturn(0L);

        List<RecommendationResponseDto> result = recommendationService.recommend(USER_ID);

        // TECH, GAME은 제외되고 ENVIRONMENT, SOCIAL만
        assertThat(result).hasSize(2);
        assertThat(result).allMatch(r -> r.getUserType() == UserType.VALUE_ORIENTED);

        List<String> returnedCategories = result.stream()
                .map(RecommendationResponseDto::getCategoryPath)
                .toList();
        assertThat(returnedCategories).doesNotContain("TECH", "GAME");
        assertThat(returnedCategories).containsAnyOf("ENVIRONMENT", "SOCIAL", "EDUCATION", "CULTURE");
    }

    @Test
    @DisplayName("가치지향형 — ENVIRONMENT(socialValue=1.0)가 CULTURE(0.7)보다 상위 랭크")
    void recommend_valueOriented_environmentRanksHigher() {
        User user = userWithType(UserType.VALUE_ORIENTED);

        // socialValue가 높은 ENVIRONMENT → 높은 순위 기대
        Project env     = openProject(1L, "ENVIRONMENT", 0L, 1_000_000L, 0L); // socialValue 1.0
        Project culture = openProject(2L, "CULTURE",     0L, 1_000_000L, 0L); // socialValue 0.7

        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(projectRepository.findAll()).thenReturn(List.of(culture, env)); // 역순으로 넣어도
        when(pledgeRepository.countBackersByProjectId(any())).thenReturn(0L);

        List<RecommendationResponseDto> result = recommendationService.recommend(USER_ID);

        assertThat(result).hasSize(2);
        // 첫 번째가 ENVIRONMENT (TOPSIS 점수 더 높음)
        assertThat(result.get(0).getCategoryPath()).isEqualTo("ENVIRONMENT");
    }

    @Test
    @DisplayName("가치지향형 — 선호 카테고리 OPEN 프로젝트 없으면 빈 리스트")
    void recommend_valueOriented_noMatchingProjects_returnsEmpty() {
        User user = userWithType(UserType.VALUE_ORIENTED);

        // TECH, GAME만 있음 → VALUE_ORIENTED 선호 카테고리 없음
        Project tech = openProject(1L, "TECH", 100_000L, 1_000_000L, 10L);

        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(projectRepository.findAll()).thenReturn(List.of(tech));

        List<RecommendationResponseDto> result = recommendationService.recommend(USER_ID);

        assertThat(result).isEmpty();
    }

    // ──────────────────────────────────────────────────────────
    // 실용지향형 (PRACTICAL_ORIENTED)
    // ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("실용지향형 — TECH, HEALTH, FOOD, FASHION, GAME만 추천")
    void recommend_practicalOriented_onlyPreferredCategories() {
        User user = userWithType(UserType.PRACTICAL_ORIENTED);

        Project tech  = openProject(1L, "TECH",        800_000L, 1_000_000L, 300L);
        Project game  = openProject(2L, "GAME",        200_000L, 1_000_000L, 80L);
        Project env   = openProject(3L, "ENVIRONMENT", 900_000L, 1_000_000L, 500L); // 제외
        Project soc   = openProject(4L, "SOCIAL",      100_000L, 1_000_000L, 30L);  // 제외

        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(projectRepository.findAll()).thenReturn(List.of(tech, game, env, soc));
        when(pledgeRepository.countBackersByProjectId(any())).thenReturn(0L);

        List<RecommendationResponseDto> result = recommendationService.recommend(USER_ID);

        assertThat(result).hasSize(2);
        List<String> returnedCategories = result.stream()
                .map(RecommendationResponseDto::getCategoryPath)
                .toList();
        assertThat(returnedCategories).doesNotContain("ENVIRONMENT", "SOCIAL");
        assertThat(returnedCategories).containsAnyOf("TECH", "HEALTH", "FOOD", "FASHION", "GAME");
    }

    @Test
    @DisplayName("실용지향형 — GAME 카테고리도 포함됨 (이전 버그 수정 확인)")
    void recommend_practicalOriented_includesGame() {
        User user = userWithType(UserType.PRACTICAL_ORIENTED);

        Project game = openProject(1L, "GAME", 500_000L, 1_000_000L, 100L);

        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(projectRepository.findAll()).thenReturn(List.of(game));
        when(pledgeRepository.countBackersByProjectId(any())).thenReturn(0L);

        List<RecommendationResponseDto> result = recommendationService.recommend(USER_ID);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getCategoryPath()).isEqualTo("GAME");
    }

    // ──────────────────────────────────────────────────────────
    // 트렌드지향형 (TREND_ORIENTED)
    // ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("트렌드지향형 — 전체 카테고리 추천 (카테고리 필터 없음)")
    void recommend_trendOriented_allCategories() {
        User user = userWithType(UserType.TREND_ORIENTED);

        Project env   = openProject(1L, "ENVIRONMENT", 300_000L, 1_000_000L, 50L);
        Project tech  = openProject(2L, "TECH",        500_000L, 1_000_000L, 200L);
        Project game  = openProject(3L, "GAME",        100_000L, 1_000_000L, 30L);
        Project etc   = openProject(4L, "ETC",         50_000L,  1_000_000L, 10L);

        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(projectRepository.findAll()).thenReturn(List.of(env, tech, game, etc));
        when(pledgeRepository.countBackersByProjectId(any())).thenReturn(0L);

        List<RecommendationResponseDto> result = recommendationService.recommend(USER_ID);

        // 전체 4개 모두 포함
        assertThat(result).hasSize(4);
        assertThat(result).allMatch(r -> r.getUserType() == UserType.TREND_ORIENTED);
    }

    @Test
    @DisplayName("트렌드지향형 — 후원자 많은 프로젝트(backerCount 높음)가 상위 랭크")
    void recommend_trendOriented_highBackerCountRanksFirst() {
        User user = userWithType(UserType.TREND_ORIENTED);

        Project popular = openProject(1L, "TECH", 0L, 1_000_000L, 0L); // id=1
        Project niche   = openProject(2L, "FOOD", 0L, 1_000_000L, 0L); // id=2

        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(projectRepository.findAll()).thenReturn(List.of(niche, popular)); // 역순 입력
        // popular: 후원자 500명, niche: 후원자 10명
        when(pledgeRepository.countBackersByProjectId(1L)).thenReturn(500L);
        when(pledgeRepository.countBackersByProjectId(2L)).thenReturn(10L);

        List<RecommendationResponseDto> result = recommendationService.recommend(USER_ID);

        assertThat(result).hasSize(2);
        // backerCount가 높은 popular(id=1, title="프로젝트-1")이 1위
        assertThat(result.get(0).getTitle()).isEqualTo("프로젝트-1");
    }

    // ──────────────────────────────────────────────────────────
    // DRAFT 프로젝트 제외 검증
    // ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("DRAFT 상태 프로젝트는 모든 성향에서 추천 제외")
    void recommend_excludesDraftProjects() {
        User user = userWithType(UserType.TREND_ORIENTED);

        Project open  = openProject(1L, "TECH", 500_000L, 1_000_000L, 100L);
        Project draft = draftProject("TECH");

        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(projectRepository.findAll()).thenReturn(List.of(open, draft));
        when(pledgeRepository.countBackersByProjectId(any())).thenReturn(0L);

        List<RecommendationResponseDto> result = recommendationService.recommend(USER_ID);

        assertThat(result).hasSize(1); // DRAFT 제외, OPEN만
    }

    // ──────────────────────────────────────────────────────────
    // 잘못된 categoryPath 방어
    // ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("DB에 잘못된 categoryPath 값이 있어도 예외 없이 해당 프로젝트만 제외")
    void recommend_invalidCategoryPath_skippedGracefully() {
        User user = userWithType(UserType.VALUE_ORIENTED);

        Project valid   = openProject(1L, "ENVIRONMENT",  300_000L, 1_000_000L, 50L);
        Project invalid = openProject(2L, "INVALID_JUNK", 900_000L, 1_000_000L, 999L);

        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(projectRepository.findAll()).thenReturn(List.of(valid, invalid));
        when(pledgeRepository.countBackersByProjectId(any())).thenReturn(0L);

        assertThatCode(() -> recommendationService.recommend(USER_ID))
                .doesNotThrowAnyException();

        List<RecommendationResponseDto> result = recommendationService.recommend(USER_ID);
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getCategoryPath()).isEqualTo("ENVIRONMENT");
    }

    // ──────────────────────────────────────────────────────────
    // TOP_N 상한 검증
    // ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("OPEN 프로젝트가 10개 초과여도 최대 10개만 반환")
    void recommend_topN_maxTen() {
        User user = userWithType(UserType.TREND_ORIENTED);

        List<Project> manyProjects = new java.util.ArrayList<>();
        for (int i = 1; i <= 15; i++) {
            manyProjects.add(openProject((long) i, "TECH", i * 10_000L, 1_000_000L, i * 5L));
        }

        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(projectRepository.findAll()).thenReturn(manyProjects);
        when(pledgeRepository.countBackersByProjectId(any())).thenReturn(0L);

        List<RecommendationResponseDto> result = recommendationService.recommend(USER_ID);

        assertThat(result).hasSizeLessThanOrEqualTo(10);
    }

    // ──────────────────────────────────────────────────────────
    // OPEN 프로젝트 0개
    // ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("OPEN 프로젝트가 아예 없으면 빈 리스트 반환")
    void recommend_noOpenProjects_returnsEmpty() {
        User user = userWithType(UserType.TREND_ORIENTED);

        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(projectRepository.findAll()).thenReturn(Collections.emptyList());

        List<RecommendationResponseDto> result = recommendationService.recommend(USER_ID);

        assertThat(result).isEmpty();
    }
}
