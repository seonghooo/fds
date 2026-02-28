package com.seongho.fds.producer;

import com.seongho.fds.dto.TransactionEvent;
import com.seongho.fds.dto.TransactionRequest;
import com.seongho.fds.dto.TransactionResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class TransactionProducer {

    private final KafkaTemplate<String, TransactionEvent> kafkaTemplate;
    private static final String TOPIC = "transaction-topic";

    public void sendTransaction(TransactionRequest request, TransactionResponse response) {
        TransactionEvent event = new TransactionEvent(request, response);
        kafkaTemplate.send(TOPIC, request.getSenderId(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Kafka 전송 실패: senderId={}, status={}",
                                request.getSenderId(), response.getStatus(), ex);
                    } else {
                        log.debug("Kafka 전송 성공: senderId={}, offset={}",
                                request.getSenderId(), result.getRecordMetadata().offset());
                    }
                });
    }
}
