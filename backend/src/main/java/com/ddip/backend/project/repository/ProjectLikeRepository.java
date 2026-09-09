package com.ddip.backend.project.repository;

import com.ddip.backend.project.domain.ProjectLike;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProjectLikeRepository extends JpaRepository<ProjectLike, Long> {
    boolean existsByProjectIdAndUserId(Long projectId, Long userId);
    void deleteByProjectIdAndUserId(Long projectId, Long userId);
    List<ProjectLike> findByUserId(Long userId);
}
