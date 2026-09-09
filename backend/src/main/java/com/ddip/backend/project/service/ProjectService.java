package com.ddip.backend.project.service;

import com.ddip.backend.common.es.document.ProjectDocument;
import com.ddip.backend.common.es.repository.ProjectElasticsearchRepository;
import com.ddip.backend.pledge.service.PledgeService;
import com.ddip.backend.project.domain.Project;
import com.ddip.backend.project.domain.ProjectImage;
import com.ddip.backend.project.domain.ProjectLike;
import com.ddip.backend.project.dto.enums.ProjectStatus;
import com.ddip.backend.project.dto.project.LikeResponseDto;
import com.ddip.backend.project.dto.project.ProjectRequestDto;
import com.ddip.backend.project.dto.project.ProjectUpdateRequestDto;
import com.ddip.backend.project.event.ProjectEsEvent;
import com.ddip.backend.project.exception.project.ProjectNotFoundException;
import com.ddip.backend.project.exception.reward.RewardTierRequiredException;
import com.ddip.backend.project.repository.ProjectLikeRepository;
import com.ddip.backend.project.repository.ProjectRepository;
import com.ddip.backend.user.repository.UserRepository;
import com.ddip.backend.user.validation.user.UserNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class ProjectService {

    private final ApplicationEventPublisher publisher;
    private final ProjectElasticsearchRepository projectElasticsearchRepository;
    private final ProjectRepository projectRepository;
    private final ProjectLikeRepository projectLikeRepository;
    private final UserRepository userRepository;
    private final ProjectImageService projectImageService;
    private final ProjectQueryService projectQueryService;

    public long createProject(List<MultipartFile> multipartFiles, ProjectRequestDto requestDto, Long userId) {
        validateRewardTiers(requestDto);
        getUserOrThrow(userId);

        Project project = createAndSaveProject(requestDto, userId);
        projectImageService.uploadProjectImagesAndSaveEntities(project, multipartFiles, requestDto.getMainIndex());
        projectImageService.syncProjectThumbnailFromMainOrThrow(project);

        indexProjectToElasticsearch(project, project.getThumbnailUrl());
        log.info("프로젝트 생성 완료 projectId={}", project.getId());
        return project.getId();
    }

    public void updateProject(List<MultipartFile> multipartFiles, Long projectId, Long userId, ProjectUpdateRequestDto requestDto) {
        Project project = projectQueryService.getProject(projectId);
        validateProjectUpdatable(project, userId, requestDto);

        List<ProjectImage> deleteTargets = projectImageService.resolveDeleteTargets(projectId, requestDto.getDeleteImageIds());
        List<ProjectImage> uploaded = projectImageService.uploadNewImagesForUpdate(projectId, project, multipartFiles);
        projectImageService.deleteProjectImages(deleteTargets);

        project.updateFrom(requestDto);
        projectImageService.applyMainImageOrThrow(project.getId(), requestDto, uploaded);

        projectImageService.syncProjectThumbnailFromMainOrThrow(project);

        publisher.publishEvent(new ProjectEsEvent(projectId));
    }

    public void deleteProject(Long projectId, Long userId) {
        Project project = projectQueryService.getProject(projectId);
        project.assertOwnedBy(userId);

        List<ProjectImage> images = projectImageService.findImagesByProjectId(projectId);
        projectImageService.deleteProjectImages(images);

        projectElasticsearchRepository.deleteById(projectId);
        projectRepository.delete(project);
        log.info("프로젝트 삭제 완료 projectId={}", projectId);
    }


    public LikeResponseDto toggleLike(Long projectId, Long userId) {
        Project project = projectRepository.findByIdForUpdate(projectId)
                .orElseThrow(() -> new ProjectNotFoundException(projectId));

        boolean alreadyLiked = projectLikeRepository.existsByProjectIdAndUserId(projectId, userId);
        if (alreadyLiked) {
            projectLikeRepository.deleteByProjectIdAndUserId(projectId, userId);
            project.decreaseLikeCount();
            return new LikeResponseDto(false, project.getLikeCount());
        }

        projectLikeRepository.save(ProjectLike.builder()
                .projectId(projectId)
                .userId(userId)
                .build());
        project.increaseLikeCount();
        return new LikeResponseDto(true, project.getLikeCount());
    }

    public LikeResponseDto getLikeStatus(Long projectId, Long userId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ProjectNotFoundException(projectId));
        boolean liked = projectLikeRepository.existsByProjectIdAndUserId(projectId, userId);
        return new LikeResponseDto(liked, project.getLikeCount());
    }

    private void validateRewardTiers(ProjectRequestDto requestDto) {
        if (requestDto.getRewardTiers() == null || requestDto.getRewardTiers().isEmpty()) {
            throw new RewardTierRequiredException();
        }
    }

    private void validateProjectUpdatable(Project project, Long userId, ProjectUpdateRequestDto requestDto) {
        project.assertOwnedBy(userId);
        project.assertEditable();
        if (requestDto.getStartAt() != null && requestDto.getEndAt() != null &&
                !requestDto.getEndAt().isAfter(requestDto.getStartAt())) {
            throw new IllegalArgumentException("종료일은 시작일 이후여야 합니다.");
        }
    }

    private void getUserOrThrow(Long userId) {
        userRepository.findById(userId).orElseThrow(() -> new UserNotFoundException(userId));
    }

    private Project createAndSaveProject(ProjectRequestDto requestDto, Long creatorId) {
        Project project = Project.toEntity(requestDto, creatorId);
        return projectRepository.save(project);
    }

    private void indexProjectToElasticsearch(Project project, String thumbnailUrl) {
        try {
            ProjectDocument document = ProjectDocument.from(project, thumbnailUrl);
            projectElasticsearchRepository.save(document);
        } catch (Exception e) {
            // ES 색인 실패는 프로젝트 생성을 막지 않음 (검색 기능은 부가 기능)
            log.warn("Elasticsearch 색인 실패 (프로젝트 생성은 완료됨) projectId={}, error={}", project.getId(), e.getMessage());
        }
    }

}