package com.seongho.fds.service;

import com.seongho.fds.domain.FraudHistory;
import com.seongho.fds.dto.TransactionRequest;
import com.seongho.fds.repository.FraudHistoryRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * FraudHistoryService 단위 테스트
 *
 * 테스트 대상 기능:
 *  - saveHistory: 이상 거래 내역 저장 (reasons 직렬화 검증)
 *  - getHistories: 전체 내역 페이지 조회 (어드민용)
 *  - getMyHistories: 특정 송금자 내역 페이지 조회 (유저용)
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("FraudHistoryService 단위 테스트")
class FraudHistoryServiceTest {

    @Mock private FraudHistoryRepository fraudHistoryRepository;
    @InjectMocks private FraudHistoryService fraudHistoryService;

    // ── saveHistory ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("saveHistory - 이상 거래 저장")
    class SaveHistory {

        @Test
        @DisplayName("reasons 여러 개 → 콤마로 합쳐서 저장됨")
        void multipleReasons_joinedWithComma() {
            List<String> reasons = List.of("고액 송금 감지", "차단된 IP");

            fraudHistoryService.saveHistory(request(), reasons);

            ArgumentCaptor<FraudHistory> captor = ArgumentCaptor.forClass(FraudHistory.class);
            verify(fraudHistoryRepository).save(captor.capture());

            FraudHistory saved = captor.getValue();
            assertThat(saved.getSenderId()).isEqualTo("sender1");
            assertThat(saved.getReceiverId()).isEqualTo("receiver1");
            assertThat(saved.getAmount()).isEqualTo(5_000L);
            assertThat(saved.getDetectedRules()).isEqualTo("고액 송금 감지, 차단된 IP");
        }

        @Test
        @DisplayName("reasons가 null → 'None'으로 저장됨")
        void nullReasons_savesNone() {
            fraudHistoryService.saveHistory(request(), null);

            ArgumentCaptor<FraudHistory> captor = ArgumentCaptor.forClass(FraudHistory.class);
            verify(fraudHistoryRepository).save(captor.capture());
            assertThat(captor.getValue().getDetectedRules()).isEqualTo("None");
        }

        @Test
        @DisplayName("reasons가 빈 리스트 → 빈 문자열로 저장됨")
        void emptyReasons_savesEmpty() {
            fraudHistoryService.saveHistory(request(), List.of());

            ArgumentCaptor<FraudHistory> captor = ArgumentCaptor.forClass(FraudHistory.class);
            verify(fraudHistoryRepository).save(captor.capture());
            assertThat(captor.getValue().getDetectedRules()).isEmpty();
        }

        @Test
        @DisplayName("저장 시 request의 timestamp가 detectedAt으로 사용됨")
        void savesRequestTimestamp() {
            LocalDateTime fixedTime = LocalDateTime.of(2026, 4, 29, 14, 0);
            TransactionRequest req = TransactionRequest.builder()
                    .senderId("s1").receiverId("r1").amount(1_000L)
                    .timestamp(fixedTime)
                    .build();

            fraudHistoryService.saveHistory(req, List.of("고액 송금 감지"));

            ArgumentCaptor<FraudHistory> captor = ArgumentCaptor.forClass(FraudHistory.class);
            verify(fraudHistoryRepository).save(captor.capture());
            assertThat(captor.getValue().getDetectedAt()).isEqualTo(fixedTime);
        }
    }

    // ── getHistories (어드민 전체 조회) ────────────────────────────────────────

    @Nested
    @DisplayName("getHistories - 전체 내역 페이지 조회 (ADMIN)")
    class GetHistories {

        @Test
        @DisplayName("findAll(pageable) 호출 → 결과 그대로 반환")
        void returnsPageFromRepository() {
            Pageable pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "detectedAt"));
            FraudHistory history = FraudHistory.builder()
                    .senderId("sender1").receiverId("receiver1")
                    .amount(1_000L).detectedRules("고액 송금 감지")
                    .detectedAt(LocalDateTime.now()).build();
            when(fraudHistoryRepository.findAll(any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(history)));

            var page = fraudHistoryService.getHistories(pageable);

            assertThat(page.getContent()).hasSize(1);
            assertThat(page.getContent().get(0).getSenderId()).isEqualTo("sender1");
        }

        @Test
        @DisplayName("결과 없을 때 빈 페이지 반환")
        void emptyResult_returnsEmptyPage() {
            when(fraudHistoryRepository.findAll(any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of()));

            var page = fraudHistoryService.getHistories(PageRequest.of(0, 20));

            assertThat(page.getContent()).isEmpty();
        }
    }

    // ── getMyHistories (유저 본인 조회) ────────────────────────────────────────

    @Nested
    @DisplayName("getMyHistories - 특정 송금자 내역 조회 (USER)")
    class GetMyHistories {

        @Test
        @DisplayName("senderId로 필터링된 결과 반환")
        void filtersBySenderId() {
            Pageable pageable = PageRequest.of(0, 10);
            FraudHistory history = FraudHistory.builder()
                    .senderId("user1").receiverId("r1")
                    .amount(500L).detectedRules("고액 송금 감지")
                    .detectedAt(LocalDateTime.now()).build();
            when(fraudHistoryRepository.findBySenderIdOrderByDetectedAtDesc(eq("user1"), any()))
                    .thenReturn(new PageImpl<>(List.of(history)));

            var page = fraudHistoryService.getMyHistories("user1", pageable);

            assertThat(page.getContent()).hasSize(1);
            assertThat(page.getContent().get(0).getSenderId()).isEqualTo("user1");
        }

        @Test
        @DisplayName("해당 유저 내역 없으면 빈 페이지 반환")
        void noHistory_returnsEmptyPage() {
            when(fraudHistoryRepository.findBySenderIdOrderByDetectedAtDesc(any(), any()))
                    .thenReturn(new PageImpl<>(List.of()));

            var page = fraudHistoryService.getMyHistories("user_no_history", PageRequest.of(0, 10));

            assertThat(page.getContent()).isEmpty();
        }
    }

    // ── 헬퍼 ─────────────────────────────────────────────────────────────────

    private TransactionRequest request() {
        return TransactionRequest.builder()
                .senderId("sender1")
                .receiverId("receiver1")
                .amount(5_000L)
                .timestamp(LocalDateTime.now())
                .build();
    }
}
