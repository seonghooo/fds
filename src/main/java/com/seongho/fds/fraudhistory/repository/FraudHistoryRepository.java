package com.seongho.fds.fraudhistory.repository;

import com.seongho.fds.fraudhistory.domain.FraudHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FraudHistoryRepository extends JpaRepository<FraudHistory, Long> {
    Page<FraudHistory> findBySenderIdOrderByDetectedAtDesc(String senderId, Pageable pageable);
}
