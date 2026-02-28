package com.seongho.fds.controller;

import com.seongho.fds.dto.TransactionRequest;
import com.seongho.fds.dto.TransactionResponse;
import com.seongho.fds.producer.TransactionProducer;
import com.seongho.fds.service.FdsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/transfer")
@RequiredArgsConstructor
public class FdsController {

    private final FdsService fdsService;
    private final TransactionProducer transactionProducer;

    @PostMapping("/check")
    public ResponseEntity<TransactionResponse> checkTransaction(@Valid @RequestBody TransactionRequest request) {
        // 1. 룰 엔진 실행 (동기 방식 탐지)
        TransactionResponse response = fdsService.validateTransaction(request);

        // 2. 탐지 결과를 Kafka로 전달 (비동기 저장용 — Consumer에서 룰 재실행 없음)
        transactionProducer.sendTransaction(request, response);

        return ResponseEntity.ok(response);
    }
}
