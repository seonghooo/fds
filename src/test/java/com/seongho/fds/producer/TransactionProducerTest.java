package com.seongho.fds.producer;

import com.seongho.fds.dto.TransactionEvent;
import com.seongho.fds.dto.TransactionRequest;
import com.seongho.fds.dto.TransactionResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("TransactionProducer - Kafka 전송 단위 테스트")
class TransactionProducerTest {

    @Mock private KafkaTemplate<String, TransactionEvent> kafkaTemplate;
    @InjectMocks private TransactionProducer transactionProducer;

    @Test
    @DisplayName("send 호출 시 올바른 토픽과 senderId(key)로 전송됨")
    void sendTransaction_correctTopicAndKey() {
        when(kafkaTemplate.send(any(), any(), any()))
                .thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));

        TransactionRequest request = request("sender1");
        TransactionResponse response = approvedResponse();

        transactionProducer.sendTransaction(request, response);

        verify(kafkaTemplate).send(eq("transaction-topic"), eq("sender1"), any(TransactionEvent.class));
    }

    @Test
    @DisplayName("send 호출 시 TransactionEvent에 request/response가 담김")
    void sendTransaction_eventContainsRequestAndResponse() {
        ArgumentCaptor<TransactionEvent> captor = ArgumentCaptor.forClass(TransactionEvent.class);
        when(kafkaTemplate.send(any(), any(), captor.capture()))
                .thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));

        TransactionRequest request = request("sender1");
        TransactionResponse response = approvedResponse();

        transactionProducer.sendTransaction(request, response);

        TransactionEvent capturedEvent = captor.getValue();
        assertThat(capturedEvent.getRequest()).isEqualTo(request);
        assertThat(capturedEvent.getResponse()).isEqualTo(response);
    }

    @Test
    @DisplayName("Kafka 전송 실패 시 예외 전파 없음 (비동기 에러 로깅)")
    void sendTransaction_onFailure_noExceptionPropagated() {
        CompletableFuture<SendResult<String, TransactionEvent>> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new RuntimeException("Kafka unavailable"));
        when(kafkaTemplate.send(any(), any(), any())).thenReturn(failedFuture);

        // 예외가 밖으로 전파되지 않아야 함
        transactionProducer.sendTransaction(request("sender1"), approvedResponse());
    }

    private TransactionRequest request(String senderId) {
        return TransactionRequest.builder()
                .senderId(senderId)
                .receiverId("receiver1")
                .amount(1_000L)
                .timestamp(LocalDateTime.now())
                .build();
    }

    private TransactionResponse approvedResponse() {
        return new TransactionResponse();
    }
}
