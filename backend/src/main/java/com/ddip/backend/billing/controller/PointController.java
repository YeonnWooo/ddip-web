package com.ddip.backend.billing.controller;

import com.ddip.backend.billing.domain.PointLedger;
import com.ddip.backend.billing.dto.PointChargeRequestDto;
import com.ddip.backend.billing.dto.PointHistoryResponseDto;
import com.ddip.backend.billing.dto.PointLedgerSource;
import com.ddip.backend.billing.dto.PointLedgerType;
import com.ddip.backend.billing.service.PointService;
import com.ddip.backend.common.security.auth.CustomUserDetails;
import com.ddip.backend.user.domain.User;
import com.ddip.backend.user.repository.UserRepository;
import com.ddip.backend.user.validation.user.UserNotFoundException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/users/points")
@RequiredArgsConstructor
@Tag(name = "Point", description = "포인트 충전/조회 API")
public class PointController {

    private final PointService pointService;
    private final UserRepository userRepository;

    @GetMapping("/balance")
    @Operation(summary = "포인트 잔액 조회")
    public ResponseEntity<Map<String, Long>> getBalance(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        User user = userRepository.findById(userDetails.getUserId())
                .orElseThrow(() -> new UserNotFoundException(userDetails.getUserId()));
        return ResponseEntity.ok(Map.of("pointBalance", user.getPointBalance()));
    }

    @PostMapping("/charge")
    @Operation(summary = "포인트 충전")
    public ResponseEntity<Map<String, Long>> charge(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody PointChargeRequestDto request) {
        Long userId = userDetails.getUserId();
        pointService.changePoint(
                userId,
                request.getAmount(),
                PointLedgerType.CHARGE,
                PointLedgerSource.CHARGE,
                userId,
                "포인트 충전"
        );
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));
        return ResponseEntity.ok(Map.of("pointBalance", user.getPointBalance()));
    }

    @GetMapping("/history")
    @Operation(summary = "포인트 내역 조회")
    public ResponseEntity<List<PointHistoryResponseDto>> getHistory(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        List<PointLedger> ledgers = pointService.getLedgersByUser(userDetails.getUserId());
        List<PointHistoryResponseDto> result = ledgers.stream()
                .map(PointHistoryResponseDto::from)
                .toList();
        return ResponseEntity.ok(result);
    }
}
