package com.seongho.fds.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

// application.yml의 fds.rules.* 값을 Java 객체로 바인딩
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "fds.rules")
public class FdsProperties {
    /** 고액 송금 기준 금액 (KRW) */
    private Long amountThreshold = 10_000_000L;

    /** 단기 반복 송금 횟수 기준 (1분 이내) */
    private Long frequencyThreshold = 5L;

    /** 차단할 송신 IP 패턴 (정규식) */
    private String blacklistedIpPattern = "192\\.168\\.0\\..*";

    /** 새벽 시간대(1~5시) 고액 송금 기준 금액 (KRW) */
    private Long nightAmountThreshold = 1_000_000L;

    /** 1분 내 분산 송금 고유 수신자 수 기준 */
    private Long distributedReceiverThreshold = 3L;

    /** 평균 대비 이상 금액 배수 기준 (평균 * N배 이상이면 감지) */
    private Long avgAmountMultiplier = 5L;
}
