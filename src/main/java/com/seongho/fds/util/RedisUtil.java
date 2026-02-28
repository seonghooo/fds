package com.seongho.fds.util;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor

public class RedisUtil {
    private final StringRedisTemplate redisTemplate;

    //특정 키의 값을 1 증가시키고 현재 값을 반환 (1분 후 자동 삭제)
    public long incrementAndGet(String key, int timeoutMinutes){
        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1){
            redisTemplate.expire(key, timeoutMinutes, TimeUnit.MINUTES);
        }
        return count != null ? count : 0;
    }
}
