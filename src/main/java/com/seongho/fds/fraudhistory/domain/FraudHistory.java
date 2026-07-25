package com.seongho.fds.fraudhistory.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

// 이상거래가 발생했을 때의 스냅샷을 저장
@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(indexes = {
        @Index(name = "idx_fraud_history_sender_id", columnList = "senderId"),
        @Index(name = "idx_fraud_history_detected_at", columnList = "detectedAt")
})
public class FraudHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String senderId;
    private String receiverId;
    private long amount;

    @Column(length = 1000)
    private String detectedRules;   // 탐지된 룰 목록(콤마로 구분하여 저장)

    private LocalDateTime detectedAt;
}