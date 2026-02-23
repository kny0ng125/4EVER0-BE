package com.team4ever.backend.global.security;

import com.team4ever.backend.domain.user.Service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;

@Component
public class CustomOAuth2SuccessHandler implements AuthenticationSuccessHandler {
    private static final String FRONTEND_URL = "http://localhost:5173";

    private final JwtTokenProvider jwtProvider;
    private final RedisService redisService;
    private final UserService userService;      // 신규회원 판단용

    public CustomOAuth2SuccessHandler(JwtTokenProvider jwtProvider,
                                      RedisService redisService,
                                      UserService userService) {
        this.jwtProvider = jwtProvider;
        this.redisService = redisService;
        this.userService = userService;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException {

        OAuth2AuthenticationToken oauthToken = (OAuth2AuthenticationToken) authentication;
        OAuth2User user = oauthToken.getPrincipal();
        String provider = oauthToken.getAuthorizedClientRegistrationId();

        // 1) OAuth 제공자별 userId 추출
        String oauthId = "google".equals(provider)
                ? user.getAttribute("sub")
                : user.getAttribute("id");
        if (oauthId == null) {
            oauthId = authentication.getName();
        }
        String email = user.getAttribute("email");
        // DEBUG 용
        System.out.println(">>> onAuthenticationSuccess: provider=" + provider + ", oauthId=" + oauthId);
        // 2) 신규 사용자 여부 판단
        boolean isNew;
        try {
            userService.getUserByUserId(oauthId);
            isNew = false;
        } catch (IllegalArgumentException ex) {
            isNew = true;
        }

        if (isNew) {
            // ▶ 신규회원: signup 페이지로 리다이렉트, provider/oathId/email 전달
            String signupUrl = UriComponentsBuilder
                    .fromUriString(FRONTEND_URL + "/signup")
                    .queryParam("provider", provider)
                    .queryParam("oauthId", oauthId)
                    .build().toUriString();
            //DEBUG 용
            System.out.println(">>> redirect to " + signupUrl);
            response.sendRedirect(signupUrl);
            return;
        }

        // ▶ 기존회원: JWT 발급 + Redis 저장 + 쿠키 세팅 + 메인으로 리다이렉트
        String accessToken  = jwtProvider.createAccessToken(oauthId);
        String refreshToken = jwtProvider.createRefreshToken(oauthId);
        redisService.storeRefreshToken(oauthId, refreshToken);

        System.out.println(">>> AT=" + accessToken);
        System.out.println(">>> RT=" + refreshToken);

        ResponseCookie cookie = ResponseCookie.from("ACCESS_TOKEN", accessToken)
                .path("/")
                .maxAge(60 * 60 * 24)
                .httpOnly(true)
                .secure(true)
                .sameSite("None")
                .build();

        response.addHeader("Set-Cookie", cookie.toString());

        response.sendRedirect(FRONTEND_URL + "/authcallback");
    }
}
