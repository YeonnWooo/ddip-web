package com.ddip.backend.project.domain;

import com.ddip.backend.project.dto.project.ProjectRequestDto;
import com.ddip.backend.project.dto.project.ProjectUpdateRequestDto;
import com.ddip.backend.project.dto.reward.RewardTierRequestDto;
import com.ddip.backend.project.dto.enums.ProjectStatus;
import com.ddip.backend.common.domain.BaseTimeEntity;
import com.ddip.backend.project.exception.project.InvalidProjectStatusException;
import com.ddip.backend.project.exception.project.ProjectAccessDeniedException;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Entity
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "projects")
public class Project extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 프로젝트 만든 사람(판매자)
    @Column(name = "creator_id", nullable = false)
    private Long creatorId;

    @Column(length = 200, nullable = false)
    private String title;

    // 프로젝트 상세 설명(긴 텍스트)
    @Lob
    private String description;

    @Column(name = "target_amount", nullable = false)
    private Long targetAmount;

    @Builder.Default
    @Column(name = "current_amount", nullable = false)
    private Long currentAmount = 0L; // 캐시(실제 근거는 pledges 합계)

    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    private ProjectStatus status;

    @Column(name = "start_at")
    private LocalDate startAt;

    @Column(name = "end_at")
    private LocalDate endAt;

    @Column(name = "thumbnail_url", length = 500)
    private String thumbnailUrl;

    @Column(name = "category_path", length = 100)
    private String categoryPath;

    // 검색 보강용 태그(쉼표로 구분: "캠핑,초경량,텐트")
    @Column(name = "tags", length = 500)
    private String tags;

    // 카드/리스트용 짧은 소개
    @Column(name = "summary", length = 200)
    private String summary;

    @Builder.Default
    @Column(name = "like_count", nullable = false)
    private Long likeCount = 0L;

    @Builder.Default
    @OneToMany(mappedBy = "project", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<RewardTier> rewardTiers = new ArrayList<>();

    @Builder.Default
    @OneToMany(mappedBy = "project", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ProjectImage> images = new ArrayList<>();

    public static Project toEntity(ProjectRequestDto requestDto, Long creatorId) {
        Project project = Project.builder()
                .title(requestDto.getTitle())
                .description(requestDto.getDescription())
                .targetAmount(requestDto.getTargetAmount())
                .creatorId(creatorId)
                .startAt(requestDto.getStartAt())
                .endAt(requestDto.getEndAt())
                .categoryPath(requestDto.getCategoryPath())
                .tags(requestDto.getTags())
                .summary(requestDto.getSummary())
                .status(ProjectStatus.DRAFT)
                .currentAmount(0L)
                .build();

        requestDto.getRewardTiers().forEach(project::addRewardTier);
        return project;
    }

    public void addRewardTier(RewardTierRequestDto dto) {
        RewardTier tier = RewardTier.builder()
                .project(this)
                .title(dto.getTitle())
                .description(dto.getDescription())
                .price(dto.getPrice())
                .limitQuantity(dto.getLimitQuantity())
                .soldQuantity(0L)
                .build();

        this.rewardTiers.add(tier);
    }

    public void updateThumbnailUrl(String thumbnailUrl) {
        this.thumbnailUrl = thumbnailUrl;
    }

    public void increaseCurrentAmount(long amount) {
        this.currentAmount += amount;
    }

    public void decreaseCurrentAmount(long amount) {
        long nextAmount = this.currentAmount - amount;
        // 음수 방지 (데이터 깨짐 방어)
        this.currentAmount = Math.max(nextAmount, 0L);
    }

    public void increaseLikeCount() {
        this.likeCount += 1;
    }

    public void decreaseLikeCount() {
        this.likeCount = Math.max(this.likeCount - 1, 0L);
    }

    public void cancel() {
        if (this.status == ProjectStatus.OPEN || this.status == ProjectStatus.DRAFT) {
            this.status = ProjectStatus.CANCELED;
        }
        else{
            throw new InvalidProjectStatusException(this.status);
        }
    }

    public void assertCancelable(){
        if (this.status != ProjectStatus.OPEN && this.status != ProjectStatus.STOP) {
            throw new InvalidProjectStatusException(this.status);
        }
    }

    public void assertBulkRefundable() {
        if (this.status != ProjectStatus.FAILED && this.status != ProjectStatus.CANCELED) {
            throw new InvalidProjectStatusException(this.status);
        }
    }

    public void openFunding() {
        this.status = ProjectStatus.OPEN;
    }

    public void assertOwnedBy(Long userId) {
        if (!this.creatorId.equals(userId)) throw new ProjectAccessDeniedException(this.id ,userId);
    }

    public void assertStatus(ProjectStatus expected) {
        if (this.status != expected) {
            throw new InvalidProjectStatusException(this.status, expected);
        }
    }

    public void assertEditable() {
        if (this.status != ProjectStatus.DRAFT) {
            throw new InvalidProjectStatusException(this.status, ProjectStatus.DRAFT);
        }
    }

    public void updateFrom(ProjectUpdateRequestDto dto) {
        this.title = dto.getTitle();
        this.description = dto.getDescription();
        this.targetAmount = dto.getTargetAmount();
        this.startAt = dto.getStartAt();
        this.endAt = dto.getEndAt();
        this.categoryPath = dto.getCategoryPath();
        this.tags = dto.getTags();
        this.summary = dto.getSummary();

        // 기존 리워드들 싹 비우고
        this.rewardTiers.clear();

        // 새 DTO 리스트 기준으로 다시 채우기
        dto.getRewardTiers().forEach(this::addRewardTier);
    }

    public boolean closeProject() {
        if (this.status != ProjectStatus.OPEN) {
            return this.status == ProjectStatus.SUCCESS; // 이미 확정된 경우 등
        }

        boolean success = this.currentAmount >= this.targetAmount;

        if (success) {
            this.status = ProjectStatus.SUCCESS;
        } else {
            this.status = ProjectStatus.FAILED;
        }

        return success;
    }

    public void rejectByAdmin() {
        log.info("해당 프로젝트는 어드민에 의해 거절 되었습니다.");
        this.status = ProjectStatus.REJECTED;
    }

    public void stopProject(){
        log.info("해당 프로젝트는 중지.");
        this.status = ProjectStatus.STOP;
    }

}
