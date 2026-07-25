package com.seongho.fds.transaction.controller;

import com.seongho.fds.fraudhistory.domain.FraudHistory;
import com.seongho.fds.transaction.dto.TransactionRequest;
import com.seongho.fds.transaction.dto.TransactionResponse;
import com.seongho.fds.transaction.producer.TransactionProducer;
import com.seongho.fds.transaction.service.FdsService;
import com.seongho.fds.fraudhistory.service.FraudHistoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

@Tag(name = "Transfer", description = "송금 요청 및 내 송금 내역 조회")
@RestController
@RequestMapping("/api/v1/transfer")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class FdsController {

    private final FdsService fdsService;
    private final TransactionProducer transactionProducer;
    private final FraudHistoryService fraudHistoryService;

    @Operation(
            summary = "송금 요청",
            description = "Drools 룰 엔진으로 6가지 이상 거래 규칙을 검사합니다.\n\n" +
                    "**탐지 규칙 목록**\n" +
                    "- 고액 송금 (1천만원 이상)\n" +
                    "- 차단 IP 접근\n" +
                    "- 단기 반복 송금 (1분 내 5회 이상)\n" +
                    "- 새벽 시간대 고액 송금 (01~05시, 100만원 이상)\n" +
                    "- 단기 분산 송금 (1분 내 3명 이상 수신자)\n" +
                    "- 평소 대비 이상 금액 (평균의 5배 이상, 최소 5건 데이터 필요)"
    )
    @PostMapping("/check")
    public ResponseEntity<TransactionResponse> checkTransaction(@Valid @RequestBody TransactionRequest request,
                                                                HttpServletRequest httpRequest) {
        String xForwardedFor = httpRequest.getHeader("X-Forwarded-For");
        String ip = (xForwardedFor != null && !xForwardedFor.isBlank())
                ? xForwardedFor.split(",")[0].trim()
                : httpRequest.getRemoteAddr();
        request.setSenderIp(ip);

        TransactionResponse response = fdsService.validateTransaction(request);
        transactionProducer.sendTransaction(request, response);

        return ResponseEntity.ok(response);
    }

    @Operation(summary = "내 송금 내역 조회", description = "로그인한 사용자의 차단된 송금 내역을 페이지 단위로 조회합니다.")
    @GetMapping("/history")
    public ResponseEntity<Page<FraudHistory>> getMyHistory(
            Principal principal,
            @PageableDefault(size = 10, sort = "detectedAt") Pageable pageable) {
        return ResponseEntity.ok(fraudHistoryService.getMyHistories(principal.getName(), pageable));
    }
}
