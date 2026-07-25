package com.seongho.fds.transaction.consumer;

import com.seongho.fds.transaction.dto.TransactionEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class TransactionDlqConsumer {

    @DltHandler
    @KafkaListener(topics = "transaction-topic.DLT", groupId = "fds-group")
    public void handleDlt(
            TransactionEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.OFFSET) long offset,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage
    ) {
        log.error("[DLQ] 최종 처리 실패 — topic={}, offset={}, senderId={}, error={}",
                topic, offset, event.getRequest().getSenderId(), exceptionMessage);
        // TODO: 알림(Slack/PagerDuty) 또는 별도 DLQ DB 테이블에 저장
    }
}
