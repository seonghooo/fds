package com.seongho.fds.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

// application.yml의 fds.rules 하위 값을 바인딩
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "fds.rules")
public class FdsProperties {
    /** 고액 송금 기준 금액 (KRW) */
    private long amountThreshold = 10_000_000;

    /** 단기 반복 송금 횟수 기준 (1분 이내) */
    private long frequencyThreshold = 5;

    /** 차단할 송신 IP 패턴 (정규식) */
    private String blacklistedIpPattern = "192\\.168\\.0\\..*";
}
