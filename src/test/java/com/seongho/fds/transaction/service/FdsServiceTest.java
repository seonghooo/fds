package com.seongho.fds.transaction.service;

import com.seongho.fds.global.config.FdsProperties;
import com.seongho.fds.transaction.dto.TransactionRequest;
import com.seongho.fds.transaction.dto.TransactionResponse;
import com.seongho.fds.whitelist.repository.TrustedReceiverRepository;
import com.seongho.fds.global.redis.RedisUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.kie.api.KieServices;
import org.kie.api.builder.KieBuilder;
import org.kie.api.builder.KieFileSystem;
import org.kie.api.builder.KieModule;
import org.kie.api.runtime.KieContainer;
import org.kie.internal.io.ResourceFactory;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

/**
 * FdsService 단위 테스트
 *
 * 테스트 전략:
 *  - KieContainer는 실제 .drl 파일을 읽어 빌드한다 (룰 로직 자체를 검증하기 위해 mock 사용 안 함)
 *  - Redis/DB 호출은 Mock으로 대체하여 외부 의존성 없이 룰만 빠르게 검증한다
 *
 * 테스트 대상 룰 6개:
 *  룰1 - 고액 송금 (amount >= 1천만원)
 *  룰2 - 차단 IP (192.168.0.*)
 *  룰3 - 단기 반복 송금 (1분 내 5회 이상)
 *  룰4 - 새벽 고액 송금 (01~05시 + 100만원 이상)
 *  룰5 - 단기 분산 송금 (1분 내 3명 이상 수신자)
 *  룰6 - 평균 대비 이상 금액 (평균의 5배 이상, 최소 5건 데이터 필요)
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("FdsService - 룰 엔진 단위 테스트")
class FdsServiceTest {

    @Mock private RedisUtil redisUtil;
    @Mock private TrustedReceiverRepository trustedReceiverRepository;
    @Mock private FdsProperties fdsProperties;

    private FdsService fdsService;

    @BeforeEach
    void setUp() {
        // 실제 .drl 파일을 읽어서 KieContainer 빌드 (DroolsConfig와 동일한 방식)
        KieServices ks = KieServices.Factory.get();
        KieFileSystem kfs = ks.newKieFileSystem();
        kfs.write(ResourceFactory.newClassPathResource("rules/transfer-rules.drl"));
        KieBuilder kb = ks.newKieBuilder(kfs);
        kb.buildAll();
        KieModule km = kb.getKieModule();
        KieContainer kieContainer = ks.newKieContainer(km.getReleaseId());

        fdsService = new FdsService(kieContainer, redisUtil, trustedReceiverRepository, fdsProperties);

        // 모든 테스트에서 kieSession.setGlobal()로 주입되므로 공통 stub
        when(fdsProperties.getAmountThreshold()).thenReturn(10_000_000L);
        when(fdsProperties.getFrequencyThreshold()).thenReturn(5L);
        when(fdsProperties.getBlacklistedIpPattern()).thenReturn("192\\.168\\.0\\..*");
        when(fdsProperties.getNightAmountThreshold()).thenReturn(1_000_000L);
        when(fdsProperties.getDistributedReceiverThreshold()).thenReturn(3L);
        when(fdsProperties.getAvgAmountMultiplier()).thenReturn(5L);

        // 분산 송금 카운트 기본값: 1명 (룰5 미발동)
        when(redisUtil.addToSetAndGetSize(any(), any(), anyInt())).thenReturn(1L);
        // 평균 송금액 기본값: 0 (데이터 부족 → 룰6 미발동)
        when(redisUtil.getUserAvgAmount(any())).thenReturn(0L);
    }

    // ── 정상 케이스 ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("정상 거래 - 어떤 룰도 적용되지 않으면 APPROVED")
    void normalTransaction_approved() {
        stubAsNonTrusted(1L); // 1분 내 1회 → 룰3 미발동

        TransactionResponse response = fdsService.validateTransaction(
                request(1_000L, LocalDateTime.of(2026, 4, 29, 14, 0)) // 오후 2시 / 소액
        );

        assertThat(response.getStatus()).isEqualTo("APPROVED");
        assertThat(response.getReasons()).isEmpty();
    }

    @Test
    @DisplayName("화이트리스트 수취인 - trusted=true이면 고액/IP/빈도 모두 통과")
    void trustedReceiver_allRulesBypassed() {
        // trusted=true 경로: getCount() 호출 (incrementAndGet 아님)
        when(trustedReceiverRepository.existsBySenderIdAndReceiverId(any(), any())).thenReturn(true);
        when(redisUtil.getCount(any())).thenReturn(0L);

        TransactionResponse response = fdsService.validateTransaction(
                request(20_000_000L, LocalDateTime.of(2026, 4, 29, 2, 0)) // 고액 + 새벽
        );

        assertThat(response.getStatus()).isEqualTo("APPROVED");
        assertThat(response.getReasons()).isEmpty();
    }

    // ── 룰1: 고액 송금 ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("룰1 - 고액 송금 감지 (1천만원 이상)")
    class Rule1HighAmount {

        @Test
        @DisplayName("정확히 임계값(1천만원) → BLOCKED")
        void exactThreshold_blocked() {
            stubAsNonTrusted(1L);

            TransactionResponse response = fdsService.validateTransaction(
                    request(10_000_000L, daytime())
            );

            assertThat(response.getStatus()).isEqualTo("BLOCKED");
            assertThat(response.getReasons().get(0)).contains("고액 송금 감지");
        }

        @Test
        @DisplayName("임계값 초과(2천만원) → BLOCKED")
        void aboveThreshold_blocked() {
            stubAsNonTrusted(1L);

            TransactionResponse response = fdsService.validateTransaction(
                    request(20_000_000L, daytime())
            );

            assertThat(response.getStatus()).isEqualTo("BLOCKED");
        }

        @Test
        @DisplayName("임계값 1원 미만(9,999,999원) → APPROVED")
        void belowThreshold_notBlocked() {
            stubAsNonTrusted(1L);

            TransactionResponse response = fdsService.validateTransaction(
                    request(9_999_999L, daytime())
            );

            assertThat(response.getStatus()).isEqualTo("APPROVED");
        }
    }

    // ── 룰2: 차단 IP ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("룰2 - 차단 IP 감지 (192.168.0.*)")
    class Rule2BlacklistedIp {

        @Test
        @DisplayName("차단 IP(192.168.0.100) → BLOCKED")
        void blacklistedIp_blocked() {
            stubAsNonTrusted(1L);

            TransactionRequest req = requestBuilder(1_000L, daytime())
                    .senderIp("192.168.0.100")
                    .build();

            TransactionResponse response = fdsService.validateTransaction(req);

            assertThat(response.getStatus()).isEqualTo("BLOCKED");
            assertThat(response.getReasons().get(0)).contains("차단된 IP");
        }

        @Test
        @DisplayName("정상 IP(10.0.0.1) → APPROVED")
        void normalIp_notBlocked() {
            stubAsNonTrusted(1L);

            TransactionRequest req = requestBuilder(1_000L, daytime())
                    .senderIp("10.0.0.1")
                    .build();

            assertThat(fdsService.validateTransaction(req).getStatus()).isEqualTo("APPROVED");
        }

        @Test
        @DisplayName("차단 대역 내 다른 IP(192.168.0.1) → BLOCKED")
        void anotherBlacklistedIp_blocked() {
            stubAsNonTrusted(1L);

            TransactionRequest req = requestBuilder(1_000L, daytime())
                    .senderIp("192.168.0.1")
                    .build();

            assertThat(fdsService.validateTransaction(req).getStatus()).isEqualTo("BLOCKED");
        }
    }

    // ── 룰3: 단기 반복 송금 ──────────────────────────────────────────────────

    @Nested
    @DisplayName("룰3 - 단기 반복 송금 감지 (1분 내 5회 이상)")
    class Rule3HighFrequency {

        @Test
        @DisplayName("정확히 임계값(5회) → BLOCKED")
        void exactThreshold_blocked() {
            stubAsNonTrusted(5L); // Redis 카운터 = 5

            TransactionResponse response = fdsService.validateTransaction(
                    request(1_000L, daytime())
            );

            assertThat(response.getStatus()).isEqualTo("BLOCKED");
            assertThat(response.getReasons().get(0)).contains("단기간 반복 송금");
        }

        @Test
        @DisplayName("임계값 1 미만(4회) → APPROVED")
        void belowThreshold_notBlocked() {
            stubAsNonTrusted(4L);

            assertThat(fdsService.validateTransaction(request(1_000L, daytime())).getStatus())
                    .isEqualTo("APPROVED");
        }
    }

    // ── 룰4: 새벽 시간대 고액 송금 ───────────────────────────────────────────

    @Nested
    @DisplayName("룰4 - 새벽 시간대 고액 송금 감지 (01~05시 + 100만원 이상)")
    class Rule4NightHighAmount {

        @Test
        @DisplayName("새벽 2시 + 100만원(임계값) → BLOCKED")
        void nightAndThresholdAmount_blocked() {
            stubAsNonTrusted(1L);

            TransactionResponse response = fdsService.validateTransaction(
                    request(1_000_000L, LocalDateTime.of(2026, 4, 29, 2, 0))
            );

            assertThat(response.getStatus()).isEqualTo("BLOCKED");
            assertThat(response.getReasons().get(0)).contains("새벽 시간대 고액 송금");
        }

        @Test
        @DisplayName("새벽 1시(경계) + 200만원 → BLOCKED")
        void hourBoundaryStart_blocked() {
            stubAsNonTrusted(1L);

            assertThat(fdsService.validateTransaction(
                    request(2_000_000L, LocalDateTime.of(2026, 4, 29, 1, 0))
            ).getStatus()).isEqualTo("BLOCKED");
        }

        @Test
        @DisplayName("새벽 5시(경계) + 200만원 → BLOCKED")
        void hourBoundaryEnd_blocked() {
            stubAsNonTrusted(1L);

            assertThat(fdsService.validateTransaction(
                    request(2_000_000L, LocalDateTime.of(2026, 4, 29, 5, 0))
            ).getStatus()).isEqualTo("BLOCKED");
        }

        @Test
        @DisplayName("새벽이지만 금액 미달(99만원) → APPROVED")
        void nightButLowAmount_notBlocked() {
            stubAsNonTrusted(1L);

            assertThat(fdsService.validateTransaction(
                    request(999_999L, LocalDateTime.of(2026, 4, 29, 2, 0))
            ).getStatus()).isEqualTo("APPROVED");
        }

        @Test
        @DisplayName("낮 시간(12시) + 100만원 → 룰4 미적용 (APPROVED)")
        void daytimeHighAmount_notTriggeredByRule4() {
            stubAsNonTrusted(1L);

            // 낮 시간 100만원은 룰1(1천만원 미만) 미적용, 룰4(새벽 아님) 미적용
            assertThat(fdsService.validateTransaction(
                    request(1_000_000L, daytime())
            ).getStatus()).isEqualTo("APPROVED");
        }
    }

    // ── 룰5: 단기 분산 송금 ──────────────────────────────────────────────────

    @Nested
    @DisplayName("룰5 - 단기 분산 송금 감지 (1분 내 3명 이상 수신자)")
    class Rule5DistributedTransfer {

        @Test
        @DisplayName("1분 내 3명에게 송금(임계값) → BLOCKED")
        void threeUniqueReceivers_blocked() {
            stubAsNonTrusted(1L);
            // setUp의 기본값(1L)을 3L로 덮어쓰기
            when(redisUtil.addToSetAndGetSize(any(), any(), anyInt())).thenReturn(3L);

            TransactionResponse response = fdsService.validateTransaction(
                    request(1_000L, daytime())
            );

            assertThat(response.getStatus()).isEqualTo("BLOCKED");
            assertThat(response.getReasons().get(0)).contains("단기 분산 송금");
        }

        @Test
        @DisplayName("1분 내 2명에게 송금(임계값 미만) → APPROVED")
        void twoUniqueReceivers_notBlocked() {
            stubAsNonTrusted(1L);
            when(redisUtil.addToSetAndGetSize(any(), any(), anyInt())).thenReturn(2L);

            assertThat(fdsService.validateTransaction(request(1_000L, daytime())).getStatus())
                    .isEqualTo("APPROVED");
        }
    }

    // ── 룰6: 평균 대비 이상 금액 ─────────────────────────────────────────────

    @Nested
    @DisplayName("룰6 - 평균 대비 이상 금액 감지 (평균 5배 이상, 최소 5건 필요)")
    class Rule6AbnormalAmount {

        @Test
        @DisplayName("평균 100만원 유저가 정확히 500만원(5배) 송금 → BLOCKED")
        void exactMultiplier_blocked() {
            stubAsNonTrusted(1L);
            when(redisUtil.getUserAvgAmount(any())).thenReturn(1_000_000L);

            TransactionResponse response = fdsService.validateTransaction(
                    request(5_000_000L, daytime())
            );

            assertThat(response.getStatus()).isEqualTo("BLOCKED");
            assertThat(response.getReasons().get(0)).contains("평소 대비 이상 금액");
        }

        @Test
        @DisplayName("거래 이력 없음(평균=0) → 룰6 미적용 (APPROVED)")
        void noHistory_skipsRule() {
            stubAsNonTrusted(1L);
            // setUp 기본값 0L — 이력 없으면 룰 조건(userAvgAmount > 0)에 걸리지 않음

            assertThat(fdsService.validateTransaction(
                    request(10_000_000_000L, daytime()) // 아무리 큰 금액이어도
            ).getReasons().stream().noneMatch(r -> r.contains("평소 대비 이상"))).isTrue();
        }

        @Test
        @DisplayName("평균 100만원 유저가 499만원(5배 미만) 송금 → APPROVED")
        void belowMultiplier_notBlocked() {
            stubAsNonTrusted(1L);
            when(redisUtil.getUserAvgAmount(any())).thenReturn(1_000_000L);

            assertThat(fdsService.validateTransaction(
                    request(4_999_999L, daytime())
            ).getStatus()).isEqualTo("APPROVED");
        }
    }

    // ── 복합 케이스 ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("복합 - 고액 + 차단 IP 동시 적용 → reasons 2개")
    void multipleRules_twoReasons() {
        stubAsNonTrusted(1L);

        TransactionRequest req = requestBuilder(15_000_000L, daytime())
                .senderIp("192.168.0.99")
                .build();

        TransactionResponse response = fdsService.validateTransaction(req);

        assertThat(response.getStatus()).isEqualTo("BLOCKED");
        assertThat(response.getReasons()).hasSize(2);
    }

    @Test
    @DisplayName("복합 - 새벽 + 분산 + 반복 동시 적용 → reasons 3개")
    void multipleRules_threeReasons() {
        stubAsNonTrusted(5L); // 룰3: 반복 5회
        when(redisUtil.addToSetAndGetSize(any(), any(), anyInt())).thenReturn(3L); // 룰5: 수신자 3명

        // 룰4: 새벽 2시 + 100만원
        TransactionResponse response = fdsService.validateTransaction(
                request(1_000_000L, LocalDateTime.of(2026, 4, 29, 2, 0))
        );

        assertThat(response.getStatus()).isEqualTo("BLOCKED");
        assertThat(response.getReasons()).hasSize(3);
    }

    // ── 헬퍼 메서드 ──────────────────────────────────────────────────────────

    /** 비신뢰 경로: incrementAndGet 반환값 지정 */
    private void stubAsNonTrusted(long txCount) {
        when(trustedReceiverRepository.existsBySenderIdAndReceiverId(any(), any())).thenReturn(false);
        when(redisUtil.incrementAndGet(any(), anyInt())).thenReturn(txCount);
    }

    private TransactionRequest request(long amount, LocalDateTime timestamp) {
        return requestBuilder(amount, timestamp).build();
    }

    private TransactionRequest.TransactionRequestBuilder requestBuilder(long amount, LocalDateTime timestamp) {
        return TransactionRequest.builder()
                .senderId("sender1")
                .receiverId("receiver1")
                .amount(amount)
                .senderIp("10.0.0.1")
                .timestamp(timestamp);
    }

    /** 룰4 외 일반 테스트용 낮 시간 */
    private LocalDateTime daytime() {
        return LocalDateTime.of(2026, 4, 29, 14, 0);
    }
}
