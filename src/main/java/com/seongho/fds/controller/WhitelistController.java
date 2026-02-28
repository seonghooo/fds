package com.seongho.fds.controller;

import com.seongho.fds.dto.WhitelistRequest;
import com.seongho.fds.service.WhitelistService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/whitelist")
@RequiredArgsConstructor
public class WhitelistController {
    private final WhitelistService whitelistService;

    @PostMapping
    public ResponseEntity<String> register(@Valid @RequestBody WhitelistRequest request){
        whitelistService.registerWhitelist(request);
        return ResponseEntity.ok("화이트리스트 등록 완료!");
    }
}
