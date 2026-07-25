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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * AuthService 단위 테스트
 *
 * 테스트 대상 기능:
 *  - 회원가입 (USER 권한)
 *  - 어드민 계정 생성 (ADMIN 권한)
 *  - 로그인 (JWT 토큰 발급)
 *  - 로그아웃 (토큰 블랙리스트 등록)
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AuthService 단위 테스트")
class AuthServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtUtil jwtUtil;
    @Mock private RedisUtil redisUtil;

    @InjectMocks
    private AuthService authService;

    // ── 회원가입 ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("회원가입")
    class Signup {

        @Test
        @DisplayName("정상 가입 - userRepository.save 호출됨")
        void success_savesCalled() {
            when(userRepository.existsByUsername("user1")).thenReturn(false);

            authService.signup(signupRequest("user1", "pass1"));

            verify(userRepository).save(any(User.class));
        }

        @Test
        @DisplayName("중복 사용자명 → IllegalArgumentException 발생, save 미호출")
        void duplicateUsername_throws() {
            when(userRepository.existsByUsername("user1")).thenReturn(true);

            assertThatThrownBy(() -> authService.signup(signupRequest("user1", "pass1")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("이미 사용 중인 사용자명");

            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("ADMIN 계정 생성 - Role.ADMIN으로 저장됨")
        void signupAdmin_savesWithAdminRole() {
            when(userRepository.existsByUsername("admin1")).thenReturn(false);

            authService.signupAdmin(signupRequest("admin1", "pass1"));

            // argThat: 실제로 저장된 User 객체의 role이 ADMIN인지 검증
            verify(userRepository).save(argThat(u -> u.getRole() == Role.ADMIN));
        }

        @Test
        @DisplayName("일반 회원가입 - Role.USER로 저장됨")
        void signup_savesWithUserRole() {
            when(userRepository.existsByUsername("user1")).thenReturn(false);

            authService.signup(signupRequest("user1", "pass1"));

            verify(userRepository).save(argThat(u -> u.getRole() == Role.USER));
        }
    }

    // ── 로그인 ────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("로그인")
    class Login {

        @Test
        @DisplayName("정상 로그인 - JWT 토큰 반환")
        void success_returnsToken() {
            User user = buildUser("user1", "encoded", Role.USER);
            when(userRepository.findByUsername("user1")).thenReturn(Optional.of(user));
            when(passwordEncoder.matches("pass1", "encoded")).thenReturn(true);
            when(jwtUtil.generateToken("user1", "USER")).thenReturn("jwt-token");

            LoginResponse response = authService.login(loginRequest("user1", "pass1"));

            assertThat(response.getToken()).isEqualTo("jwt-token");
        }

        @Test
        @DisplayName("존재하지 않는 사용자 → AuthenticationFailedException 발생")
        void userNotFound_throws() {
            when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> authService.login(loginRequest("ghost", "pass")))
                    .isInstanceOf(AuthenticationFailedException.class);
        }

        @Test
        @DisplayName("비밀번호 불일치 → AuthenticationFailedException 발생")
        void wrongPassword_throws() {
            User user = buildUser("user1", "encoded", Role.USER);
            when(userRepository.findByUsername("user1")).thenReturn(Optional.of(user));
            when(passwordEncoder.matches("wrong", "encoded")).thenReturn(false);

            assertThatThrownBy(() -> authService.login(loginRequest("user1", "wrong")))
                    .isInstanceOf(AuthenticationFailedException.class);
        }
    }

    // ── 로그아웃 ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("로그아웃")
    class Logout {

        @Test
        @DisplayName("유효한 토큰 → Redis 블랙리스트에 등록됨")
        void validToken_addedToBlacklist() {
            long remainingMs = 60_000L; // 1분 남음
            when(jwtUtil.getExpirationTime("valid-token"))
                    .thenReturn(System.currentTimeMillis() + remainingMs);

            authService.logout("valid-token");

            // 남은 시간이 양수일 때만 블랙리스트 등록 호출
            verify(redisUtil).addToBlacklist(eq("valid-token"), anyLong());
        }

        @Test
        @DisplayName("이미 만료된 토큰 → 블랙리스트 등록 안됨")
        void expiredToken_notAddedToBlacklist() {
            // 이미 만료 → remaining <= 0
            when(jwtUtil.getExpirationTime("expired-token"))
                    .thenReturn(System.currentTimeMillis() - 1_000L);

            authService.logout("expired-token");

            verify(redisUtil, never()).addToBlacklist(any(), anyLong());
        }
    }

    // ── 헬퍼 ─────────────────────────────────────────────────────────────────

    private SignupRequest signupRequest(String username, String password) {
        SignupRequest req = new SignupRequest();
        req.setUsername(username);
        req.setPassword(password);
        return req;
    }

    private LoginRequest loginRequest(String username, String password) {
        LoginRequest req = new LoginRequest();
        req.setUsername(username);
        req.setPassword(password);
        return req;
    }

    private User buildUser(String username, String encodedPassword, Role role) {
        return User.builder()
                .username(username)
                .password(encodedPassword)
                .role(role)
                .build();
    }
}
