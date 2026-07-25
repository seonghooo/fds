package com.seongho.fds.service;

import com.seongho.fds.domain.FraudHistory;
import com.seongho.fds.dto.TransactionRequest;
import com.seongho.fds.repository.FraudHistoryRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;

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
                .detectedAt(request.getTimestamp())
                .build();

        fraudHistoryRepository.save(history);
    }

    public Page<FraudHistory> getHistories(Pageable pageable) {
        return fraudHistoryRepository.findAll(pageable);
    }

    public Page<FraudHistory> getMyHistories(String senderId, Pageable pageable) {
        return fraudHistoryRepository.findBySenderIdOrderByDetectedAtDesc(senderId, pageable);
    }
}
