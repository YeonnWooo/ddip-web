package com.ddip.backend.common.security;

import com.ddip.backend.common.security.auth.CustomUserDetails;
import com.ddip.backend.common.security.auth.CustomUserDetailsService;
import com.ddip.backend.common.security.auth.JwtUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    private final JwtUtils jwtUtils;
    private final CustomUserDetailsService userDetailsService;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor == null) return message;

        // ── CONNECT: JWT 검증 후 Principal 세팅 ──────────────────────────────
        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            String rawAuth = accessor.getFirstNativeHeader("Authorization");
            if (rawAuth != null && rawAuth.startsWith("Bearer ")) {
                String token = rawAuth.substring(7).trim();
                try {
                    String email = jwtUtils.extractUserEmail(token);
                    if (!jwtUtils.isTokenExpired(token)) {
                        CustomUserDetails userDetails =
                                (CustomUserDetails) userDetailsService.loadUserByUsername(email);
                        Authentication auth = new UsernamePasswordAuthenticationToken(
                                userDetails, null, userDetails.getAuthorities());
                        accessor.setUser(auth);
                        log.debug("[STOMP] CONNECT 인증 성공 userId={}", userDetails.getUserId());
                    }
                } catch (Exception e) {
                    log.warn("[STOMP] CONNECT 인증 실패: {}", e.getMessage());
                }
            }
        }

        // ── SUBSCRIBE: 알림 토픽에 대해서는 본인 것만 구독 허용 ─────────────
        if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            String destination = accessor.getDestination();
            if (destination != null && destination.startsWith("/topic/notification/")) {
                Authentication auth = (Authentication) accessor.getUser();
                if (auth == null || !auth.isAuthenticated()) {
                    log.warn("[STOMP] 알림 구독 거부: 미인증 사용자");
                    throw new SecurityException("알림 구독에는 인증이 필요합니다.");
                }
                CustomUserDetails userDetails = (CustomUserDetails) auth.getPrincipal();
                String targetUserId = destination.replace("/topic/notification/", "");
                if (!userDetails.getUserId().toString().equals(targetUserId)) {
                    log.warn("[STOMP] 알림 구독 거부: userId={} → 요청 대상={}", userDetails.getUserId(), targetUserId);
                    throw new SecurityException("다른 사용자의 알림을 구독할 수 없습니다.");
                }
            }
        }

        return message;
    }
}
