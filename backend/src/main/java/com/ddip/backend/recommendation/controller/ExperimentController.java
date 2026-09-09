package com.ddip.backend.recommendation.controller;

import com.ddip.backend.common.security.auth.CustomUserDetails;
import com.ddip.backend.recommendation.dto.ExperimentResponseDto;
import com.ddip.backend.recommendation.dto.UserComparisonDto;
import com.ddip.backend.recommendation.service.ExperimentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/experiment")
@Tag(name = "Experiment", description = "AHP vs Fuzzy AHP 실험 비교 API")
public class ExperimentController {

    private final ExperimentService experimentService;

    @GetMapping("/my-comparison")
    @Operation(
            summary = "내 성향 AHP vs Fuzzy AHP 개인화 비교",
            description = """
                    로그인 유저의 성향(UserType)과 선호 카테고리를 적용하여
                    AHP 추천 결과와 Fuzzy AHP 추천 결과를 나란히 반환합니다.
                    - 기존 recommend/projects와 동일한 카테고리 필터 적용
                    - 두 방법의 Top 10 랭킹 및 Spearman 순위 상관계수 포함
                    인증 필요
                    """
    )
    public ResponseEntity<UserComparisonDto> myComparison(
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        return ResponseEntity.ok(experimentService.myComparison(userDetails.getUserId()));
    }

    @GetMapping("/ahp-comparison")
    @Operation(
            summary = "AHP vs Fuzzy AHP 가중치 및 TOPSIS 랭킹 비교",
            description = """
                    기존 AHP(Saaty 고유벡터법)와 Fuzzy AHP(Chang's Extent Analysis 1996)를 비교합니다.
                    - 가중치 벡터 (9개 기준)
                    - 전체 OPEN 프로젝트에 대한 TOPSIS 랭킹
                    - Spearman 순위 상관계수 (두 방법의 결과 유사성)
                    인증 불필요 (실험용 공개 엔드포인트)
                    """
    )
    public ResponseEntity<ExperimentResponseDto> compare() {
        return ResponseEntity.ok(experimentService.compare());
    }
}
