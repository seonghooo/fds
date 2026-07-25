package com.seongho.fds.transaction.consumer;

import com.seongho.fds.transaction.dto.TransactionEvent;
import com.seongho.fds.fraudhistory.service.FraudHistoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class TransactionConsumer {

    private final FraudHistoryService fraudHistoryService;

    @KafkaListener(topics = "transaction-topic", groupId = "fds-group")
    public void consume(TransactionEvent event) {
        var request = event.getRequest();
        var response = event.getResponse();

        log.info("Kafka로부터 거래 수신: {}", request.getSenderId());

        if ("BLOCKED".equals(response.getStatus())) {
            log.warn("!!! [비동기 탐지] 이상 거래 발견: {}", response.getReasons());
            fraudHistoryService.saveHistory(request, response.getReasons());
            log.info("탐지 내역 DB 저장 완료");
        }
    }
}
