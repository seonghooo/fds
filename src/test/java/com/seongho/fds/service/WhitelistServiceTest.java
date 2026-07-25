package com.seongho.fds.service;

import com.seongho.fds.domain.TrustedReceiver;
import com.seongho.fds.dto.WhitelistRequest;
import com.seongho.fds.exception.DuplicateResourceException;
import com.seongho.fds.repository.TrustedReceiverRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * WhitelistService 단위 테스트
 *
 * 테스트 대상 기능:
 *  - registerWhitelist: 화이트리스트 등록 (중복 방지)
 *  - getAll: 전체 목록 조회
 *  - delete: 항목 삭제
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("WhitelistService 단위 테스트")
class WhitelistServiceTest {

    @Mock private TrustedReceiverRepository trustedReceiverRepository;
    @InjectMocks private WhitelistService whitelistService;

    // ── 등록 ──────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("registerWhitelist - 화이트리스트 등록")
    class Register {

        @Test
        @DisplayName("정상 등록 - senderId/receiverId가 올바르게 저장됨")
        void success_correctDataSaved() {
            when(trustedReceiverRepository.existsBySenderIdAndReceiverId("A", "B")).thenReturn(false);

            whitelistService.registerWhitelist(request("A", "B"));

            ArgumentCaptor<TrustedReceiver> captor = ArgumentCaptor.forClass(TrustedReceiver.class);
            verify(trustedReceiverRepository).save(captor.capture());
            assertThat(captor.getValue().getSenderId()).isEqualTo("A");
            assertThat(captor.getValue().getReceiverId()).isEqualTo("B");
        }

        @Test
        @DisplayName("중복 등록 → DuplicateResourceException 발생, save 미호출")
        void duplicate_throwsDuplicateException() {
            when(trustedReceiverRepository.existsBySenderIdAndReceiverId("A", "B")).thenReturn(true);

            assertThatThrownBy(() -> whitelistService.registerWhitelist(request("A", "B")))
                    .isInstanceOf(DuplicateResourceException.class)
                    .hasMessageContaining("이미 화이트리스트에 등록된");

            verify(trustedReceiverRepository, never()).save(any());
        }

        @Test
        @DisplayName("A→B 등록과 B→A 등록은 별개 (방향이 다른 쌍은 독립)")
        void reverseDirection_treatedSeparately() {
            // A→B는 이미 등록됨, B→A는 미등록
            when(trustedReceiverRepository.existsBySenderIdAndReceiverId("A", "B")).thenReturn(true);
            when(trustedReceiverRepository.existsBySenderIdAndReceiverId("B", "A")).thenReturn(false);

            // A→B는 예외
            assertThatThrownBy(() -> whitelistService.registerWhitelist(request("A", "B")))
                    .isInstanceOf(DuplicateResourceException.class);

            // B→A는 정상 저장
            whitelistService.registerWhitelist(request("B", "A"));
            verify(trustedReceiverRepository).save(any());
        }
    }

    // ── 전체 조회 ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getAll - 전체 목록 조회")
    class GetAll {

        @Test
        @DisplayName("전체 화이트리스트 목록 반환")
        void returnsAllEntries() {
            TrustedReceiver entry = TrustedReceiver.builder()
                    .senderId("A").receiverId("B").build();
            when(trustedReceiverRepository.findAll()).thenReturn(List.of(entry));

            List<TrustedReceiver> result = whitelistService.getAll();

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getSenderId()).isEqualTo("A");
        }

        @Test
        @DisplayName("등록된 항목 없으면 빈 리스트 반환")
        void noEntries_returnsEmptyList() {
            when(trustedReceiverRepository.findAll()).thenReturn(List.of());

            assertThat(whitelistService.getAll()).isEmpty();
        }
    }

    // ── 삭제 ──────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("delete - 항목 삭제")
    class Delete {

        @Test
        @DisplayName("ID로 삭제 - deleteById 호출됨")
        void delete_callsDeleteById() {
            whitelistService.delete(1L);

            verify(trustedReceiverRepository).deleteById(1L);
        }

        @Test
        @DisplayName("다른 ID를 삭제해도 deleteById에 해당 ID가 전달됨")
        void delete_correctIdPassed() {
            whitelistService.delete(42L);

            verify(trustedReceiverRepository).deleteById(42L);
        }
    }

    // ── 헬퍼 ─────────────────────────────────────────────────────────────────

    private WhitelistRequest request(String senderId, String receiverId) {
        WhitelistRequest req = new WhitelistRequest();
        req.setSenderId(senderId);
        req.setReceiverId(receiverId);
        return req;
    }
}
