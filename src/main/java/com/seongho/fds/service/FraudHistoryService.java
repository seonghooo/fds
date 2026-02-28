package com.seongho.fds.service;

import com.seongho.fds.domain.FraudHistory;
import com.seongho.fds.dto.TransactionRequest;
import com.seongho.fds.repository.FraudHistoryRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List; // saveHistory 파라미터에서 사용

@Service
@RequiredArgsConstructor
public class FraudHistoryService {

    private final FraudHistoryRepository fraudHistoryRepository;

    @Transactional
    public void saveHistory(TransactionRequest request, List<String> reasons){
        FraudHistory history = FraudHistory.builder()
                .senderId(request.getSenderId())
                .receiverId(request.getReceiverId())
                .amount(request.getAmount())
                // reasons가 null일 경우 대비해 안전하게 처리
                .detectedRules(reasons != null ? String.join(", ", reasons) : "None")
                .detectedAt(LocalDateTime.now())
                .build();

        fraudHistoryRepository.save(history);
    }

    // 관리자가 이상 로그 목록 확인 (최신순 페이지네이션)
    public Page<FraudHistory> getHistories(Pageable pageable) {
        return fraudHistoryRepository.findAll(pageable);
    }
}
