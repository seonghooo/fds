package com.seongho.fds.service;

import com.seongho.fds.domain.Role;
import com.seongho.fds.domain.User;
import com.seongho.fds.dto.LoginRequest;
import com.seongho.fds.dto.LoginResponse;
import com.seongho.fds.dto.SignupRequest;
import com.seongho.fds.exception.AuthenticationFailedException;
import com.seongho.fds.repository.UserRepository;
import com.seongho.fds.util.JwtUtil;
import com.seongho.fds.util.RedisUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final RedisUtil redisUtil;

    public void signup(SignupRequest request) {
        createUser(request, Role.USER);
    }

    public void signupAdmin(SignupRequest request) {
        createUser(request, Role.ADMIN);
    }

    private void createUser(SignupRequest request, Role role) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new IllegalArgumentException("이미 사용 중인 사용자명입니다.");
        }

        User user = User.builder()
                .username(request.getUsername())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(role)
                .build();

        userRepository.save(user);
    }

    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new AuthenticationFailedException("사용자명 또는 비밀번호가 올바르지 않습니다."));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new AuthenticationFailedException("사용자명 또는 비밀번호가 올바르지 않습니다.");
        }

        String token = jwtUtil.generateToken(user.getUsername(), user.getRole().name());
        return new LoginResponse(token);
    }

    public void logout(String token) {
        long remaining = jwtUtil.getExpirationTime(token) - System.currentTimeMillis();
        if (remaining > 0) {
            redisUtil.addToBlacklist(token, remaining);
        }
    }
}
