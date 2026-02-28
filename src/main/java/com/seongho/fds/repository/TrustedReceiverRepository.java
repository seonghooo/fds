package com.seongho.fds.repository;

import com.seongho.fds.domain.TrustedReceiver;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TrustedReceiverRepository extends JpaRepository<TrustedReceiver, Long> {
    // 송금자와 수취인이 화이트리스트에 존재하는지 확인
    boolean existsBySenderIdAndReceiverId(String senderId, String receiverId);
}
