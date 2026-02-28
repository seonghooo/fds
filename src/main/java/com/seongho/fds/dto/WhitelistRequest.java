package com.seongho.fds.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class WhitelistRequest {
    @NotBlank(message = "송금자 ID는 필수입니다.")
    private String senderId;

    @NotBlank(message = "수취인 ID는 필수입니다.")
    private String receiverId;
}
