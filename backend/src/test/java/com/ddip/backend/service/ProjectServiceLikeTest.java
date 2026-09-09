package com.ddip.backend.service;

import com.ddip.backend.project.domain.Project;
import com.ddip.backend.project.domain.ProjectLike;
import com.ddip.backend.project.dto.enums.ProjectStatus;
import com.ddip.backend.project.dto.project.LikeResponseDto;
import com.ddip.backend.project.exception.project.ProjectNotFoundException;
import com.ddip.backend.project.repository.ProjectLikeRepository;
import com.ddip.backend.project.repository.ProjectRepository;
import com.ddip.backend.project.service.ProjectImageService;
import com.ddip.backend.project.service.ProjectQueryService;
import com.ddip.backend.project.service.ProjectService;
import com.ddip.backend.project.es.repository.ProjectElasticsearchRepository;
import com.ddip.backend.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProjectService — 좋아요 기능 단위 테스트")
class ProjectServiceLikeTest {

    @InjectMocks
    private ProjectService projectService;

    @Mock private ApplicationEventPublisher publisher;
    @Mock private ProjectElasticsearchRepository projectElasticsearchRepository;
    @Mock private ProjectRepository projectRepository;
    @Mock private ProjectLikeRepository projectLikeRepository;
    @Mock private UserRepository userRepository;
    @Mock private ProjectImageService projectImageService;
    @Mock private ProjectQueryService projectQueryService;

    private Project project;

    @BeforeEach
    void setUp() {
        project = Project.builder()
                .id(1L).creatorId(10L).title("좋아요 테스트")
                .targetAmount(100_000L).currentAmount(0L)
                .status(ProjectStatus.OPEN).likeCount(0L).build();
    }

    @Test
    @DisplayName("좋아요를 누르지 않은 상태에서 토글하면 좋아요가 추가되고 liked=true 반환")
    void toggleLike_add() {
        given(projectRepository.findByIdForUpdate(1L)).willReturn(Optional.of(project));
        given(projectLikeRepository.existsByProjectIdAndUserId(1L, 1L)).willReturn(false);
        given(projectLikeRepository.save(any(ProjectLike.class))).willAnswer(inv -> inv.getArgument(0));

        LikeResponseDto result = projectService.toggleLike(1L, 1L);

        assertThat(result.isLiked()).isTrue();
        assertThat(result.getLikeCount()).isEqualTo(1L);
        then(projectLikeRepository).should(times(1)).save(any(ProjectLike.class));
    }

    @Test
    @DisplayName("이미 좋아요한 상태에서 토글하면 좋아요가 취소되고 liked=false 반환")
    void toggleLike_remove() {
        Project likedProject = Project.builder()
                .id(1L).creatorId(10L).title("좋아요 테스트")
                .targetAmount(100_000L).currentAmount(0L)
                .status(ProjectStatus.OPEN).likeCount(3L).build();

        given(projectRepository.findByIdForUpdate(1L)).willReturn(Optional.of(likedProject));
        given(projectLikeRepository.existsByProjectIdAndUserId(1L, 1L)).willReturn(true);

        LikeResponseDto result = projectService.toggleLike(1L, 1L);

        assertThat(result.isLiked()).isFalse();
        assertThat(result.getLikeCount()).isEqualTo(2L);
        then(projectLikeRepository).should(times(1)).deleteByProjectIdAndUserId(1L, 1L);
    }

    @Test
    @DisplayName("존재하지 않는 프로젝트에 좋아요 시 ProjectNotFoundException 발생")
    void toggleLike_projectNotFound() {
        given(projectRepository.findByIdForUpdate(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> projectService.toggleLike(99L, 1L))
                .isInstanceOf(ProjectNotFoundException.class);
    }

    @Test
    @DisplayName("getLikeStatus — 좋아요한 경우 liked=true 와 likeCount 반환")
    void getLikeStatus_liked() {
        given(projectRepository.findById(1L)).willReturn(Optional.of(project));
        given(projectLikeRepository.existsByProjectIdAndUserId(1L, 1L)).willReturn(true);

        LikeResponseDto result = projectService.getLikeStatus(1L, 1L);

        assertThat(result.isLiked()).isTrue();
    }

    @Test
    @DisplayName("getLikeStatus — 좋아요하지 않은 경우 liked=false 반환")
    void getLikeStatus_notLiked() {
        given(projectRepository.findById(1L)).willReturn(Optional.of(project));
        given(projectLikeRepository.existsByProjectIdAndUserId(1L, 1L)).willReturn(false);

        LikeResponseDto result = projectService.getLikeStatus(1L, 1L);

        assertThat(result.isLiked()).isFalse();
    }
}
