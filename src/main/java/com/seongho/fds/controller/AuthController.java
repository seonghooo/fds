package com.seongho.fds.controller;

import com.seongho.fds.dto.LoginRequest;
import com.seongho.fds.dto.LoginResponse;
import com.seongho.fds.dto.SignupRequest;
import com.seongho.fds.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/api/v1/auth/signup")
    public ResponseEntity<String> signup(@Valid @RequestBody SignupRequest request) {
        authService.signup(request);
        return ResponseEntity.ok("회원가입 완료!");
    }

    @PostMapping("/api/v1/auth/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/api/v1/admin/users")
    public ResponseEntity<String> signupAdmin(@Valid @RequestBody SignupRequest request) {
        authService.signupAdmin(request);
        return ResponseEntity.ok("ADMIN 계정 생성 완료!");
    }
}
