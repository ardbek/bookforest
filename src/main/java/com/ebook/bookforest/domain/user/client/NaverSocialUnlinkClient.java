package com.ebook.bookforest.domain.user.client;

import com.ebook.bookforest.domain.user.config.SocialUnlinkProperties;
import com.ebook.bookforest.domain.user.enums.ProviderTokenType;
import com.ebook.bookforest.domain.user.enums.SocialProvider;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * 네이버 연동 해제 클라이언트
 * 저장해 둔 사용자 토큰을 폐기 API로 삭제하는 방식
 *
 * <p>실패 시 재시도 여부
 * <ul>
 *   <li>429, 5xx, 통신 실패: 재시도</li>
 *   <li>그 외 4xx: 재시도하지 않음</li>
 * </ul>
 */
@Component
public class NaverSocialUnlinkClient implements SocialUnlinkClient {

    private static final String REVOKE_URL = "https://nid.naver.com/oauth2.0/revoke";

    private final RestClient restClient;
    private final SocialUnlinkProperties properties;

    public NaverSocialUnlinkClient(RestClient.Builder restClientBuilder, SocialUnlinkProperties properties) {
        this.restClient = restClientBuilder.build();
        this.properties = properties;
    }

    @Override
    public boolean supports(SocialProvider provider) {
        return provider == SocialProvider.NAVER;
    }

    @Override
    public void unlink(SocialUnlinkCommand command) {
        validateCommand(command);

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", properties.naverClientId());
        form.add("client_secret", properties.naverClientSecret());
        form.add("token", command.providerToken());
        form.add("token_type_hint", convertTokenType(command.providerTokenType()));

        try {
            restClient
                .post()
                .uri(REVOKE_URL)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .toBodilessEntity();
        } catch (RestClientResponseException exception) {
            throw convertHttpException(exception);
        } catch (RestClientException exception) {
            throw new SocialUnlinkClientException(
                true,
                "NAVER_NETWORK_ERROR",
                "네이버 토큰 폐기 API 통신에 실패했습니다.",
                exception
            );
        }
    }

    private void validateCommand(SocialUnlinkCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("연동 해제 명령은 필수입니다.");
        }
        if (command.provider() != SocialProvider.NAVER) {
            throw new IllegalArgumentException("네이버 연동 해제 명령이 아닙니다.");
        }
        if (command.providerSubject() == null || command.providerSubject().isBlank()) {
            throw new IllegalArgumentException("네이버 사용자 식별값은 필수입니다.");
        }
        if (command.providerToken() == null || command.providerToken().isBlank()) {
            throw new IllegalArgumentException("네이버 토큰은 필수입니다.");
        }
        if (command.providerTokenType() == null) {
            throw new IllegalArgumentException("네이버 토큰 종류는 필수입니다.");
        }
    }

    private String convertTokenType(ProviderTokenType tokenType) {
        return switch (tokenType) {
            case ACCESS_TOKEN -> "access_token";
            case REFRESH_TOKEN -> "refresh_token";
        };
    }

    private SocialUnlinkClientException convertHttpException(RestClientResponseException exception) {
        int statusCode = exception.getStatusCode().value();
        boolean retryable = statusCode == 429 || exception.getStatusCode().is5xxServerError();

        String errorCode = switch (statusCode) {
            case 400 -> "NAVER_INVALID_REQUEST";
            case 401 -> "NAVER_UNAUTHORIZED_CLIENT";
            case 429 -> "NAVER_TOO_MANY_REQUESTS";
            case 503 -> "NAVER_TEMPORARILY_UNAVAILABLE";
            default -> "NAVER_HTTP_" + statusCode;
        };

        return new SocialUnlinkClientException(
            retryable,
            errorCode,
            "네이버 토큰 폐기 API가 오류를 반환했습니다.",
            exception
        );
    }
}
