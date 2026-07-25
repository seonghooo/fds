package com.seongho.fds.config;

import com.seongho.fds.util.JwtUtil;
import com.seongho.fds.util.RedisUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

// API 요청이 들어올 때마다 사용자가 들고온 JWT가 진짜인지,유효한지 확인하고 통과시켜주는 역할
@Component
@RequiredArgsConstructor

// OncePerRequestFilter : 모든 http 요청마다 딱 한 번만 실행됨.
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final RedisUtil redisUtil;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        // 1. HTTP 요청 헤더에서 Authorization 필드에 담긴 토큰 추출
        String authHeader = request.getHeader("Authorization");

        // 2. 토큰 유효성 검증 (ID 확인) (헤더가 없거나 Bearer로 시작하지 않으면 다음 필터로 넘김)
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);

            // 토큰 유효성 검증 (토큰의 서명 맞는지, 만료되지 않았는지 확인, 블랙리스트 여부)
            if (jwtUtil.validateToken(token) && !redisUtil.isBlacklisted(token)) {
                // 검증 완료되면, 토큰에서 username과 role을 꺼내 UsernamePasswordAuthenticationToken 객체 만듦

                String username = jwtUtil.extractUsername(token);
                String role = jwtUtil.extractRole(token);

                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(
                                username,
                                null,
                                List.of(new SimpleGrantedAuthority("ROLE_" + role))
                        );

                //3. SecurityContext에 인증 정보 저장
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        }

        // 4.다음 필터로 전달 (Controller에 전달)
        filterChain.doFilter(request, response);
    }
}
