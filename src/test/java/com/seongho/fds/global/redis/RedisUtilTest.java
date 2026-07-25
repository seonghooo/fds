package com.seongho.fds.global.redis;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * RedisUtil 단위 테스트
 *
 * 테스트 대상 기능:
 *  - incrementAndGet: 카운트 증가 + TTL 자동 설정
 *  - addToSetAndGetSize: Set에 수신자 추가 + 고유 수신자 수 반환
 *  - getUserAvgAmount: 평균 송금액 계산 (최소 5건 기준)
 *  - updateAvgAmount: 평균 누적 갱신
 *
 * 핵심 불변 조건:
 *  - TTL이 없는(-1) 키에만 expire를 호출한다 (INCR/EXPIRE 비원자 크래시 복구)
 *  - TTL이 이미 있으면 expire를 호출하지 않는다 (불필요한 TTL 리셋 방지)
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RedisUtil 단위 테스트")
class RedisUtilTest {

    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOperations;
    @Mock private SetOperations<String, String> setOperations;

    private RedisUtil redisUtil;

    @BeforeEach
    void setUp() {
        // 테스트마다 opsForValue / opsForSet 사용 여부가 다르므로 lenient 적용
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().when(redisTemplate.opsForSet()).thenReturn(setOperations);
        redisUtil = new RedisUtil(redisTemplate);
    }

    // ── incrementAndGet ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("incrementAndGet - 카운트 증가 및 TTL 관리")
    class IncrementAndGet {

        @Test
        @DisplayName("최초 증가(TTL 없음) → TTL 설정 호출됨")
        void firstIncrement_setsTtl() {
            when(valueOperations.increment("tx_count:user1")).thenReturn(1L);
            when(redisTemplate.getExpire("tx_count:user1", TimeUnit.SECONDS)).thenReturn(-1L);

            long result = redisUtil.incrementAndGet("tx_count:user1", 1);

            assertThat(result).isEqualTo(1L);
            verify(redisTemplate).expire("tx_count:user1", 1, TimeUnit.MINUTES);
        }

        @Test
        @DisplayName("TTL 존재 시 → TTL 재설정 안됨")
        void ttlExists_doesNotResetTtl() {
            when(valueOperations.increment("tx_count:user1")).thenReturn(2L);
            when(redisTemplate.getExpire("tx_count:user1", TimeUnit.SECONDS)).thenReturn(30L);

            redisUtil.incrementAndGet("tx_count:user1", 1);

            verify(redisTemplate, never()).expire(any(), anyLong(), any());
        }

        @Test
        @DisplayName("카운트 > 1이지만 TTL 없음(크래시 복구 시나리오) → TTL 강제 설정")
        void highCountNoTtl_recoverySetsExpire() {
            when(valueOperations.increment("tx_count:user1")).thenReturn(3L);
            when(redisTemplate.getExpire("tx_count:user1", TimeUnit.SECONDS)).thenReturn(-1L);

            long result = redisUtil.incrementAndGet("tx_count:user1", 1);

            assertThat(result).isEqualTo(3L);
            verify(redisTemplate).expire("tx_count:user1", 1, TimeUnit.MINUTES);
        }

        @Test
        @DisplayName("increment 결과 null → 0 반환 (방어 처리)")
        void nullResult_returnsZero() {
            when(valueOperations.increment(any())).thenReturn(null);

            assertThat(redisUtil.incrementAndGet("tx_count:user1", 1)).isZero();
        }

        @Test
        @DisplayName("서로 다른 senderId는 독립적인 카운터를 사용함")
        void differentSenders_independentCounters() {
            when(valueOperations.increment("tx_count:userA")).thenReturn(1L);
            when(valueOperations.increment("tx_count:userB")).thenReturn(5L);
            when(redisTemplate.getExpire("tx_count:userA", TimeUnit.SECONDS)).thenReturn(-1L);
            when(redisTemplate.getExpire("tx_count:userB", TimeUnit.SECONDS)).thenReturn(50L);

            long a = redisUtil.incrementAndGet("tx_count:userA", 1);
            long b = redisUtil.incrementAndGet("tx_count:userB", 1);

            assertThat(a).isEqualTo(1L);
            assertThat(b).isEqualTo(5L);
        }
    }

    // ── addToSetAndGetSize ────────────────────────────────────────────────────

    @Nested
    @DisplayName("addToSetAndGetSize - 분산 송금 수신자 카운팅")
    class AddToSetAndGetSize {

        @Test
        @DisplayName("최초 수신자 추가(TTL 없음) → TTL 설정 후 Set 크기 반환")
        void firstAdd_setsTtlAndReturnsSize() {
            when(redisTemplate.getExpire("tx_receivers:user1", TimeUnit.SECONDS)).thenReturn(-1L);
            when(setOperations.size("tx_receivers:user1")).thenReturn(1L);

            long result = redisUtil.addToSetAndGetSize("tx_receivers:user1", "receiverA", 1);

            assertThat(result).isEqualTo(1L);
            verify(setOperations).add("tx_receivers:user1", "receiverA");
            verify(redisTemplate).expire("tx_receivers:user1", 1, TimeUnit.MINUTES);
        }

        @Test
        @DisplayName("TTL 존재 시 → TTL 재설정 안됨")
        void ttlExists_doesNotResetTtl() {
            when(redisTemplate.getExpire("tx_receivers:user1", TimeUnit.SECONDS)).thenReturn(30L);
            when(setOperations.size("tx_receivers:user1")).thenReturn(2L);

            redisUtil.addToSetAndGetSize("tx_receivers:user1", "receiverB", 1);

            verify(redisTemplate, never()).expire(any(), anyLong(), any());
        }

        @Test
        @DisplayName("Set size가 null이면 0 반환 (방어 처리)")
        void nullSize_returnsZero() {
            when(redisTemplate.getExpire(any(), any())).thenReturn(30L);
            when(setOperations.size(any())).thenReturn(null);

            assertThat(redisUtil.addToSetAndGetSize("tx_receivers:user1", "r1", 1)).isZero();
        }
    }

    // ── getUserAvgAmount ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("getUserAvgAmount - 유저 평균 송금액 조회")
    class GetUserAvgAmount {

        @Test
        @DisplayName("데이터 없음(sum/count null) → 0 반환")
        void noData_returnsZero() {
            when(valueOperations.get("avg_amount:sum:user1")).thenReturn(null);
            when(valueOperations.get("avg_amount:count:user1")).thenReturn(null);

            assertThat(redisUtil.getUserAvgAmount("user1")).isZero();
        }

        @Test
        @DisplayName("거래 건수 < 5 → 신뢰도 부족으로 0 반환")
        void lessThanFiveTransactions_returnsZero() {
            when(valueOperations.get("avg_amount:sum:user1")).thenReturn("8000000");
            when(valueOperations.get("avg_amount:count:user1")).thenReturn("4"); // 4건

            assertThat(redisUtil.getUserAvgAmount("user1")).isZero();
        }

        @Test
        @DisplayName("정확히 5건 → 평균 계산 반환 (1,600,000원)")
        void exactlyFiveTransactions_returnsAverage() {
            when(valueOperations.get("avg_amount:sum:user1")).thenReturn("8000000");
            when(valueOperations.get("avg_amount:count:user1")).thenReturn("5");

            assertThat(redisUtil.getUserAvgAmount("user1")).isEqualTo(1_600_000L);
        }

        @Test
        @DisplayName("10건 이상 → 정상 평균 반환")
        void moreThanFiveTransactions_returnsAverage() {
            when(valueOperations.get("avg_amount:sum:user1")).thenReturn("10000000");
            when(valueOperations.get("avg_amount:count:user1")).thenReturn("10");

            assertThat(redisUtil.getUserAvgAmount("user1")).isEqualTo(1_000_000L);
        }
    }

    // ── updateAvgAmount ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("updateAvgAmount - 평균 송금액 갱신")
    class UpdateAvgAmount {

        @Test
        @DisplayName("최초 업데이트(TTL 없음) → sum/count 증가 + 30일 TTL 설정")
        void firstUpdate_setsTtl() {
            when(redisTemplate.getExpire("avg_amount:sum:user1", TimeUnit.SECONDS)).thenReturn(-1L);

            redisUtil.updateAvgAmount("user1", 500_000L);

            verify(valueOperations).increment("avg_amount:sum:user1", 500_000L);
            verify(valueOperations).increment("avg_amount:count:user1");
            verify(redisTemplate).expire("avg_amount:sum:user1", 30, TimeUnit.DAYS);
            verify(redisTemplate).expire("avg_amount:count:user1", 30, TimeUnit.DAYS);
        }

        @Test
        @DisplayName("TTL 존재 시 → sum/count만 증가, TTL 재설정 안됨")
        void ttlExists_onlyIncrementsValues() {
            when(redisTemplate.getExpire("avg_amount:sum:user1", TimeUnit.SECONDS)).thenReturn(100_000L);

            redisUtil.updateAvgAmount("user1", 300_000L);

            verify(valueOperations).increment("avg_amount:sum:user1", 300_000L);
            verify(valueOperations).increment("avg_amount:count:user1");
            verify(redisTemplate, never()).expire(any(), anyLong(), eq(TimeUnit.DAYS));
        }
    }
}
