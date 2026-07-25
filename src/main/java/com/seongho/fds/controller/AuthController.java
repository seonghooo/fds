package com.seongho.fds.controller;

import com.seongho.fds.dto.LoginRequest;
import com.seongho.fds.dto.LoginResponse;
import com.seongho.fds.dto.SignupRequest;
import com.seongho.fds.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Auth", description = "회원가입 / 로그인 / 로그아웃")
@RestController
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "회원가입")
    @PostMapping("/api/v1/auth/signup")
    public ResponseEntity<String> signup(@Valid @RequestBody SignupRequest request) {
        authService.signup(request);
        return ResponseEntity.ok("회원가입 완료!");
    }

    @Operation(summary = "로그인", description = "성공 시 JWT 토큰 반환. 이후 요청에서 Authorize 버튼으로 토큰을 등록하세요.")
    @PostMapping("/api/v1/auth/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @Operation(summary = "로그아웃", description = "현재 토큰을 블랙리스트에 등록하여 무효화")
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/api/v1/auth/logout")
    public ResponseEntity<String> logout(@RequestHeader("Authorization") String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            authService.logout(authHeader.substring(7));
        }
        return ResponseEntity.ok("로그아웃 완료!");
    }

    @Operation(summary = "ADMIN 계정 생성", description = "ADMIN 권한 계정 생성 (ADMIN만 접근 가능)")
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/api/v1/admin/users")
    public ResponseEntity<String> signupAdmin(@Valid @RequestBody SignupRequest request) {
        authService.signupAdmin(request);
        return ResponseEntity.ok("ADMIN 계정 생성 완료!");
    }
}
