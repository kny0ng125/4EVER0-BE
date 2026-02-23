package com.team4ever.backend.global.security;

import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class CustomOAuth2UserService implements OAuth2UserService<OAuth2UserRequest, OAuth2User> {

    private final DefaultOAuth2UserService delegate = new DefaultOAuth2UserService();

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User user = delegate.loadUser(userRequest);
        String regId = userRequest.getClientRegistration().getRegistrationId();
        Map<String, Object> attributes = user.getAttributes();

        String userId;
        String email;
        String name;

        switch (regId) {
            case "google":
                userId = (String) attributes.get("sub");
                email = (String) attributes.get("email");
                name = (String) attributes.get("name");
                break;

            case "kakao":
                userId = String.valueOf(attributes.get("id"));
                // 안전하지 않은 연산 경고 해결: 명시적 캐스팅과 어노테이션 사용
                @SuppressWarnings("unchecked")
                Map<String, Object> kakaoAccount = (Map<String, Object>) attributes.get("kakao_account");
                email = (String) kakaoAccount.get("email");

                @SuppressWarnings("unchecked")
                Map<String, Object> profile = (Map<String, Object>) kakaoAccount.get("profile");
                name = (String) profile.get("nickname");
                break;

            case "naver":
                @SuppressWarnings("unchecked")
                Map<String, Object> response = (Map<String, Object>) attributes.get("response");
                userId = (String) response.get("id");
                email = (String) response.get("email");
                name = (String) response.get("name");
                break;

            default:
                throw new OAuth2AuthenticationException("Unknown provider: " + regId);
        }

        // 권한은 기본 ROLE_USER
        return new DefaultOAuth2User(
                user.getAuthorities(),
                Map.of(
                        "id", userId,
                        "email", email != null ? email : "", // Null safety
                        "name", name != null ? name : ""
                ),
                "id"
        );
    }
}