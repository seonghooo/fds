package com.seongho.fds.controller;

import com.seongho.fds.domain.FraudHistory;
import com.seongho.fds.repository.FraudHistoryRepository;
import com.seongho.fds.service.FraudHistoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/fraud-history")
@RequiredArgsConstructor
public class FraudHistoryController {
    private final FraudHistoryService fraudHistoryService;

    @GetMapping
    public ResponseEntity<List<FraudHistory>> getHistories(){
        return ResponseEntity.ok(fraudHistoryService.getAllHistories());
    }
}
