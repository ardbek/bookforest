package com.ebook.bookforest.global.security.oauth.unlink;

import com.ebook.bookforest.domain.user.enums.SocialProvider;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
public class KakaoSocialUnlinkClient implements SocialUnlinkClient {

    private static final String UNLINK_URL =
        "https://kapi.kakao.com/v1/user/unlink";

    private final RestClient restClient;
    private final SocialUnlinkProperties properties;

    public KakaoSocialUnlinkClient(
        RestClient.Builder restClientBuilder,
        SocialUnlinkProperties properties
    ) {
        this.restClient = restClientBuilder.build();
        this.properties = properties;
    }

    @Override
    public boolean supports(SocialProvider provider) {
        return provider == SocialProvider.KAKAO;
    }

    @Override
    public void unlink(SocialUnlinkCommand command) {
        validateCommand(command);

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("target_id_type", "user_id");
        form.add("target_id", command.providerSubject());

        try {
            KakaoUnlinkResponse response = restClient
                .post()
                .uri(UNLINK_URL)
                .header(
                    HttpHeaders.AUTHORIZATION,
                    "KakaoAK " + properties.kakaoAdminKey()
                )
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(KakaoUnlinkResponse.class);

            validateResponse(command, response);
        } catch (RestClientResponseException exception) {
            throw convertHttpException(exception);
        } catch (RestClientException exception) {
            throw new SocialUnlinkClientException(
                true,
                "KAKAO_NETWORK_ERROR",
                "카카오 연동 해제 API 통신에 실패했습니다.",
                exception
            );
        }
    }

    private void validateCommand(SocialUnlinkCommand command) {
        if (command == null) {
            throw new IllegalArgumentException(
                "연동 해제 명령은 필수입니다."
            );
        }

        if (command.provider() != SocialProvider.KAKAO) {
            throw new IllegalArgumentException(
                "카카오 연동 해제 명령이 아닙니다."
            );
        }

        if (
            command.providerSubject() == null
                || command.providerSubject().isBlank()
        ) {
            throw new IllegalArgumentException(
                "카카오 사용자 식별값은 필수입니다."
            );
        }
    }

    private void validateResponse(
        SocialUnlinkCommand command,
        KakaoUnlinkResponse response
    ) {
        if (response == null || response.id() == null) {
            throw new SocialUnlinkClientException(
                true,
                "KAKAO_INVALID_RESPONSE",
                "카카오 연동 해제 응답이 올바르지 않습니다."
            );
        }

        if (!command.providerSubject().equals(response.id().toString())) {
            throw new SocialUnlinkClientException(
                false,
                "KAKAO_USER_ID_MISMATCH",
                "카카오 연동 해제 사용자 식별값이 일치하지 않습니다."
            );
        }
    }

    private SocialUnlinkClientException convertHttpException(
        RestClientResponseException exception
    ) {
        int statusCode = exception.getStatusCode().value();

        boolean retryable =
            statusCode == 429
                || exception.getStatusCode().is5xxServerError();

        return new SocialUnlinkClientException(
            retryable,
            "KAKAO_HTTP_" + statusCode,
            "카카오 연동 해제 API가 오류를 반환했습니다.",
            exception
        );
    }

    private record KakaoUnlinkResponse(Long id) {
    }
}
