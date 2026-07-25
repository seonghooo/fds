package com.seongho.fds.consumer;

import com.seongho.fds.dto.TransactionEvent;
import com.seongho.fds.dto.TransactionRequest;
import com.seongho.fds.dto.TransactionResponse;
import com.seongho.fds.service.FraudHistoryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("TransactionConsumer - Kafka 이벤트 처리 단위 테스트")
class TransactionConsumerTest {

    @Mock private FraudHistoryService fraudHistoryService;
    @InjectMocks private TransactionConsumer transactionConsumer;

    @Test
    @DisplayName("BLOCKED 이벤트 수신 → FraudHistoryService.saveHistory 호출됨")
    void consume_blockedEvent_savesHistory() {
        TransactionRequest request = request();
        TransactionResponse response = blockedResponse(List.of("고액 송금 감지"));
        TransactionEvent event = new TransactionEvent(request, response);

        transactionConsumer.consume(event);

        verify(fraudHistoryService).saveHistory(request, List.of("고액 송금 감지"));
    }

    @Test
    @DisplayName("APPROVED 이벤트 수신 → FraudHistoryService 호출 안됨")
    void consume_approvedEvent_doesNotSaveHistory() {
        TransactionEvent event = new TransactionEvent(request(), new TransactionResponse());

        transactionConsumer.consume(event);

        verifyNoInteractions(fraudHistoryService);
    }

    @Test
    @DisplayName("BLOCKED 이벤트 - reasons 여러 개 → 그대로 전달됨")
    void consume_blockedEvent_multipleReasons_allPassed() {
        List<String> reasons = List.of("고액 송금 감지", "차단된 IP 대역으로부터의 접근입니다.");
        TransactionResponse response = blockedResponse(reasons);
        TransactionEvent event = new TransactionEvent(request(), response);

        transactionConsumer.consume(event);

        verify(fraudHistoryService).saveHistory(any(), eq(reasons));
    }

    private TransactionRequest request() {
        return TransactionRequest.builder()
                .senderId("sender1")
                .receiverId("receiver1")
                .amount(1_000L)
                .timestamp(LocalDateTime.now())
                .build();
    }

    private TransactionResponse blockedResponse(List<String> reasons) {
        TransactionResponse response = new TransactionResponse();
        reasons.forEach(response::addReason);
        return response;
    }
}
