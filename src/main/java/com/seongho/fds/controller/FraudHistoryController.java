package com.seongho.fds.controller;

import com.seongho.fds.domain.FraudHistory;
import com.seongho.fds.service.FraudHistoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/fraud-history")
@RequiredArgsConstructor
public class FraudHistoryController {
    private final FraudHistoryService fraudHistoryService;

    @GetMapping
    public ResponseEntity<Page<FraudHistory>> getHistories(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "detectedAt"));
        return ResponseEntity.ok(fraudHistoryService.getHistories(pageable));
    }
}
