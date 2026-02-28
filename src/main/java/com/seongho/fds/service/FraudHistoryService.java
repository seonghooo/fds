package com.seongho.fds.service;

import com.seongho.fds.domain.FraudHistory;
import com.seongho.fds.dto.TransactionRequest;
import com.seongho.fds.repository.FraudHistoryRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
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
                .detectedAt(LocalDateTime.now())
                .build();

        fraudHistoryRepository.save(history);
    }

    // 관리자가 이상 로그 목록 확인
    public List<FraudHistory> getAllHistories(){
        return fraudHistoryRepository.findAll();
        // 최신순으로 하려면 findAll(Sort.by(Sort.Direction.DESC, "detectedAt"))
    }
}
