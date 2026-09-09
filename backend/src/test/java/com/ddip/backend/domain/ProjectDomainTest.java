package com.ddip.backend.domain;

import com.ddip.backend.project.domain.Project;
import com.ddip.backend.project.dto.enums.ProjectStatus;
import com.ddip.backend.project.exception.project.InvalidProjectStatusException;
import com.ddip.backend.project.exception.project.ProjectAccessDeniedException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

@DisplayName("Project 도메인 단위 테스트")
class ProjectDomainTest {

    private Project buildProject(ProjectStatus status, long current, long target) {
        return Project.builder()
                .id(1L).creatorId(10L).title("테스트")
                .currentAmount(current).targetAmount(target)
                .status(status).build();
    }

    // ─────────────────────────────────────────────────────────────────
    // closeProject
    // ─────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("closeProject()")
    class CloseProject {

        @Test
        @DisplayName("currentAmount >= targetAmount 이면 SUCCESS 로 전환되고 true 반환")
        void closeProject_success() {
            Project project = buildProject(ProjectStatus.OPEN, 100_000L, 100_000L);

            boolean result = project.closeProject();

            assertThat(result).isTrue();
            assertThat(project.getStatus()).isEqualTo(ProjectStatus.SUCCESS);
        }

        @Test
        @DisplayName("currentAmount < targetAmount 이면 FAILED 로 전환되고 false 반환")
        void closeProject_failed() {
            Project project = buildProject(ProjectStatus.OPEN, 50_000L, 100_000L);

            boolean result = project.closeProject();

            assertThat(result).isFalse();
            assertThat(project.getStatus()).isEqualTo(ProjectStatus.FAILED);
        }

        @Test
        @DisplayName("이미 SUCCESS 상태이면 true 반환하고 상태 변경 없음")
        void closeProject_alreadySuccess() {
            Project project = buildProject(ProjectStatus.SUCCESS, 100_000L, 100_000L);

            boolean result = project.closeProject();

            assertThat(result).isTrue();
            assertThat(project.getStatus()).isEqualTo(ProjectStatus.SUCCESS);
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // increaseCurrentAmount / decreaseCurrentAmount
    // ─────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("모금액 증감")
    class AmountChange {

        @Test
        @DisplayName("increaseCurrentAmount 호출 시 currentAmount 가 증가한다")
        void increaseCurrentAmount() {
            Project project = buildProject(ProjectStatus.OPEN, 0L, 100_000L);

            project.increaseCurrentAmount(30_000L);

            assertThat(project.getCurrentAmount()).isEqualTo(30_000L);
        }

        @Test
        @DisplayName("decreaseCurrentAmount 호출 시 currentAmount 가 감소한다")
        void decreaseCurrentAmount() {
            Project project = buildProject(ProjectStatus.OPEN, 30_000L, 100_000L);

            project.decreaseCurrentAmount(10_000L);

            assertThat(project.getCurrentAmount()).isEqualTo(20_000L);
        }

        @Test
        @DisplayName("decreaseCurrentAmount 가 잔액보다 크면 0으로 클램핑된다")
        void decreaseCurrentAmount_clampToZero() {
            Project project = buildProject(ProjectStatus.OPEN, 5_000L, 100_000L);

            project.decreaseCurrentAmount(10_000L);

            assertThat(project.getCurrentAmount()).isEqualTo(0L);
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // likeCount
    // ─────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("좋아요 수 증감")
    class LikeCount {

        @Test
        @DisplayName("increaseLikeCount 호출 시 likeCount 가 1 증가한다")
        void increaseLikeCount() {
            Project project = buildProject(ProjectStatus.OPEN, 0L, 100_000L);

            project.increaseLikeCount();
            project.increaseLikeCount();

            assertThat(project.getLikeCount()).isEqualTo(2L);
        }

        @Test
        @DisplayName("decreaseLikeCount 호출 시 likeCount 가 1 감소한다")
        void decreaseLikeCount() {
            Project project = Project.builder()
                    .id(1L).creatorId(10L).title("좋아요 테스트")
                    .targetAmount(100_000L).currentAmount(0L)
                    .status(ProjectStatus.OPEN).likeCount(3L).build();

            project.decreaseLikeCount();

            assertThat(project.getLikeCount()).isEqualTo(2L);
        }

        @Test
        @DisplayName("likeCount 가 0일 때 decreaseLikeCount 를 호출해도 음수가 되지 않는다")
        void decreaseLikeCount_clampToZero() {
            Project project = buildProject(ProjectStatus.OPEN, 0L, 100_000L);

            project.decreaseLikeCount(); // 0에서 감소 시도

            assertThat(project.getLikeCount()).isEqualTo(0L);
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // assertOwnedBy / assertCancelable / assertBulkRefundable
    // ─────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("도메인 검증 메서드")
    class Assertions {

        @Test
        @DisplayName("소유자가 아닌 userId 로 assertOwnedBy 시 ProjectAccessDeniedException 발생")
        void assertOwnedBy_denied() {
            Project project = buildProject(ProjectStatus.OPEN, 0L, 100_000L);

            assertThatThrownBy(() -> project.assertOwnedBy(99L))
                    .isInstanceOf(ProjectAccessDeniedException.class);
        }

        @Test
        @DisplayName("소유자 userId 로 assertOwnedBy 시 예외 없음")
        void assertOwnedBy_ok() {
            Project project = buildProject(ProjectStatus.OPEN, 0L, 100_000L);

            assertThatNoException().isThrownBy(() -> project.assertOwnedBy(10L));
        }

        @Test
        @DisplayName("OPEN 상태에서 assertCancelable 은 통과한다")
        void assertCancelable_open() {
            Project project = buildProject(ProjectStatus.OPEN, 0L, 100_000L);

            assertThatNoException().isThrownBy(project::assertCancelable);
        }

        @Test
        @DisplayName("SUCCESS 상태에서 assertCancelable 은 예외를 던진다")
        void assertCancelable_success_throws() {
            Project project = buildProject(ProjectStatus.SUCCESS, 100_000L, 100_000L);

            assertThatThrownBy(project::assertCancelable)
                    .isInstanceOf(InvalidProjectStatusException.class);
        }

        @Test
        @DisplayName("FAILED 상태에서 assertBulkRefundable 은 통과한다")
        void assertBulkRefundable_failed() {
            Project project = buildProject(ProjectStatus.FAILED, 0L, 100_000L);

            assertThatNoException().isThrownBy(project::assertBulkRefundable);
        }

        @Test
        @DisplayName("OPEN 상태에서 assertBulkRefundable 은 예외를 던진다")
        void assertBulkRefundable_open_throws() {
            Project project = buildProject(ProjectStatus.OPEN, 0L, 100_000L);

            assertThatThrownBy(project::assertBulkRefundable)
                    .isInstanceOf(InvalidProjectStatusException.class);
        }
    }
}
