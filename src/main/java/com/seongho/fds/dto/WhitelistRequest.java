package com.seongho.fds.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class WhitelistRequest {
    private String senderId;
    private String receiverId;
}
