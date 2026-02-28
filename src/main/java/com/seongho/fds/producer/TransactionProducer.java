package com.seongho.fds.producer;

import com.seongho.fds.dto.TransactionEvent;
import com.seongho.fds.dto.TransactionRequest;
import com.seongho.fds.dto.TransactionResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TransactionProducer {

    private final KafkaTemplate<String, TransactionEvent> kafkaTemplate;
    private static final String TOPIC = "transaction-topic";

    public void sendTransaction(TransactionRequest request, TransactionResponse response) {
        TransactionEvent event = new TransactionEvent(request, response);
        kafkaTemplate.send(TOPIC, request.getSenderId(), event);
    }
}
