package com.seongho.fds.transaction.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.LocalDateTime;

@Setter
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor

public class TransactionRequest {
    @NotBlank(message = "송금자 ID는 필수입니다. ")
    private String senderId;

    @NotBlank(message = "수취인 ID는 필수입니다. ")
    private String receiverId;

    @Min(value = 1, message = "송금 금액은 0보다 커야 합니다.")
    private long amount;

    private String senderIp;        //접속 IP(이상 IP 탐지용)

    private String deviceId;        // 접속 기기 ID (기기 변경 탐지용)

    @NotNull
    private LocalDateTime timestamp;    //거래 발생 시간 ("1 내 5번 송금" 같은 빈도 기반 룰을 만들 때 기준점이 됌)

    @JsonIgnore
    private Long recentTransactionCount;        // 최근 1분간 거래 횟수 (Redis)

    @JsonIgnore
    private Long recentUniqueReceiverCount;     // 최근 1분간 고유 수신자 수 (Redis Set)

    @JsonIgnore
    private Long userAvgAmount;                 // 유저의 과거 평균 송금액 (Redis)

    @JsonIgnore
    private Boolean trusted;              // Drools 룰 엔진이 이 거래가 안전한 거래인지 판단
}
