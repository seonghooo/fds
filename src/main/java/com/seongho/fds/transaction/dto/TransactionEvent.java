package com.seongho.fds.transaction.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// Kafka 메시지 래퍼 — 룰 엔진 결과를 Consumer에 전달해 중복 실행 방지
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TransactionEvent {
    private TransactionRequest request;
    private TransactionResponse response;
}
