package com.seongho.fds.fraudhistory.controller;

import com.seongho.fds.fraudhistory.domain.FraudHistory;
import com.seongho.fds.fraudhistory.service.FraudHistoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin - Fraud History", description = "전체 이상 거래 내역 조회 (ADMIN 전용)")
@RestController
@RequestMapping("/api/v1/admin/fraud-history")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class FraudHistoryController {
    private final FraudHistoryService fraudHistoryService;

    @Operation(summary = "전체 이상 거래 내역 조회", description = "모든 사용자의 차단된 거래 내역을 최신순으로 조회합니다. ADMIN 권한 필요.")
    @GetMapping
    public ResponseEntity<Page<FraudHistory>> getHistories(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "detectedAt"));
        return ResponseEntity.ok(fraudHistoryService.getHistories(pageable));
    }
}
