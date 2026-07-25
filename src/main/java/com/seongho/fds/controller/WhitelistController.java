package com.seongho.fds.controller;

import com.seongho.fds.domain.TrustedReceiver;
import com.seongho.fds.dto.WhitelistRequest;
import com.seongho.fds.service.WhitelistService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Admin - Whitelist", description = "화이트리스트 관리 (ADMIN 전용)")
@RestController
@RequestMapping("/api/v1/whitelist")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class WhitelistController {
    private final WhitelistService whitelistService;

    @Operation(summary = "화이트리스트 등록", description = "송금자-수취인 쌍을 등록합니다. 등록된 쌍은 빈도 기반 탐지 규칙에서 제외됩니다.")
    @PostMapping
    public ResponseEntity<String> register(@Valid @RequestBody WhitelistRequest request){
        whitelistService.registerWhitelist(request);
        return ResponseEntity.ok("화이트리스트 등록 완료!");
    }

    @Operation(summary = "화이트리스트 전체 조회")
    @GetMapping
    public ResponseEntity<List<TrustedReceiver>> getAll() {
        return ResponseEntity.ok(whitelistService.getAll());
    }

    @Operation(summary = "화이트리스트 항목 삭제")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        whitelistService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
