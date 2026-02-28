package com.seongho.fds.repository;

import com.seongho.fds.domain.FraudHistory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FraudHistoryRepository extends JpaRepository<FraudHistory, Long> {
        // 최신순 정렬 등을 위해 기본 메서드만 있어도 충분함
}
