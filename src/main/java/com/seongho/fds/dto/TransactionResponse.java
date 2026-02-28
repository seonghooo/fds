package com.seongho.fds.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@AllArgsConstructor
// 탐지 결과 DTO
public class TransactionResponse {
    private String status;      // "APPROVED" (정상), "BLOCKED" (차단)
    private List<String> reasons;   //탐지된 룰 목록

    public TransactionResponse() {
        this.status = "APPROVED";
        this.reasons = new ArrayList<>();
    }

    public void addReason(String reason){
        this.status = "BLOCKED";        //룰에 하나라도 걸리면 차단 상태로 변경
        this.reasons.add(reason);
    }
}
