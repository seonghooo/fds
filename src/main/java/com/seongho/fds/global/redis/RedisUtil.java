package com.seongho.fds.global.redis;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor

public class RedisUtil {
    private final StringRedisTemplate redisTemplate;

    // 현재 카운트 값만 조회 (키 없으면 0 반환)
    public long getCount(String key) {
        String value = redisTemplate.opsForValue().get(key);
        return value != null ? Long.parseLong(value) : 0;
    }

    public void addToBlacklist(String token, long remainingMs) {
        redisTemplate.opsForValue().set("blacklist:" + token, "logout", remainingMs, TimeUnit.MILLISECONDS);
    }

    public boolean isBlacklisted(String token) {
        return Boolean.TRUE.equals(redisTemplate.hasKey("blacklist:" + token));
    }

    // 특정 키의 값을 1 증가시키고 현재 값을 반환 (1분 후 자동 삭제)
    // TTL이 없는 키(-1)를 감지해 EXPIRE를 보장함으로써 INCR/EXPIRE 비원자 구간의 크래시 복구 처리
    public long incrementAndGet(String key, int timeoutMinutes){
        Long count = redisTemplate.opsForValue().increment(key);
        Long ttl = redisTemplate.getExpire(key, TimeUnit.SECONDS);
        if (ttl != null && ttl == -1) {
            redisTemplate.expire(key, timeoutMinutes, TimeUnit.MINUTES);
        }
        return count != null ? count : 0;
    }

    // Set에 수신자 ID를 추가하고 고유 수신자 수를 반환 (1분 TTL)
    public long addToSetAndGetSize(String key, String value, int timeoutMinutes) {
        redisTemplate.opsForSet().add(key, value);
        Long ttl = redisTemplate.getExpire(key, TimeUnit.SECONDS);
        if (ttl != null && ttl == -1) {
            redisTemplate.expire(key, timeoutMinutes, TimeUnit.MINUTES);
        }
        Long size = redisTemplate.opsForSet().size(key);
        return size != null ? size : 0;
    }

    // 유저의 과거 평균 송금액 조회 (데이터 5건 미만이면 0 반환 — 평균 신뢰도 확보)
    public long getUserAvgAmount(String senderId) {
        String sumStr = redisTemplate.opsForValue().get("avg_amount:sum:" + senderId);
        String countStr = redisTemplate.opsForValue().get("avg_amount:count:" + senderId);
        if (sumStr == null || countStr == null) return 0;
        long count = Long.parseLong(countStr);
        if (count < 5) return 0;
        return Long.parseLong(sumStr) / count;
    }

    // 거래 후 평균 송금액 누적 갱신 (30일 TTL)
    public void updateAvgAmount(String senderId, long amount) {
        String sumKey = "avg_amount:sum:" + senderId;
        String countKey = "avg_amount:count:" + senderId;
        redisTemplate.opsForValue().increment(sumKey, amount);
        redisTemplate.opsForValue().increment(countKey);
        Long ttl = redisTemplate.getExpire(sumKey, TimeUnit.SECONDS);
        if (ttl != null && ttl == -1) {
            redisTemplate.expire(sumKey, 30, TimeUnit.DAYS);
            redisTemplate.expire(countKey, 30, TimeUnit.DAYS);
        }
    }
}
