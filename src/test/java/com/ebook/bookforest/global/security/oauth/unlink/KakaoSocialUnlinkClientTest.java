package com.ebook.bookforest.global.security.oauth.unlink;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.ebook.bookforest.domain.user.enums.SocialProvider;
import java.net.SocketTimeoutException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class KakaoSocialUnlinkClientTest {

    private static final String UNLINK_URL =
        "https://kapi.kakao.com/v1/user/unlink";
    private static final String ADMIN_KEY = "kakao-admin-key";

    private MockRestServiceServer server;
    private KakaoSocialUnlinkClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new KakaoSocialUnlinkClient(
            builder,
            properties()
        );
    }

    @AfterEach
    void verifyRequests() {
        server.verify();
    }

    @Test
    void 카카오_사용자_ID와_Admin_Key로_연동을_해제한다() {
        server.expect(requestTo(UNLINK_URL))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header(
                HttpHeaders.AUTHORIZATION,
                "KakaoAK " + ADMIN_KEY
            ))
            .andExpect(content().contentTypeCompatibleWith(
                MediaType.APPLICATION_FORM_URLENCODED
            ))
            .andExpect(content().string(
                "target_id_type=user_id&target_id=123456789"
            ))
            .andRespond(withSuccess(
                "{\"id\":123456789}",
                MediaType.APPLICATION_JSON
            ));

        client.unlink(command("123456789"));
    }

    @Test
    void 카카오_5xx_응답은_재시도_가능한_오류로_변환한다() {
        server.expect(requestTo(UNLINK_URL))
            .andRespond(withServerError());

        SocialUnlinkClientException exception = assertThrows(
            SocialUnlinkClientException.class,
            () -> client.unlink(command("123456789"))
        );

        assertTrue(exception.isRetryable());
        assertEquals("KAKAO_HTTP_500", exception.getErrorCode());
    }

    @Test
    void 카카오_400_응답은_영구_오류로_변환한다() {
        server.expect(requestTo(UNLINK_URL))
            .andRespond(withBadRequest());

        SocialUnlinkClientException exception = assertThrows(
            SocialUnlinkClientException.class,
            () -> client.unlink(command("123456789"))
        );

        assertFalse(exception.isRetryable());
        assertEquals("KAKAO_HTTP_400", exception.getErrorCode());
    }

    @Test
    void 카카오_응답_ID가_요청_ID와_다르면_실패한다() {
        server.expect(requestTo(UNLINK_URL))
            .andRespond(withSuccess(
                "{\"id\":987654321}",
                MediaType.APPLICATION_JSON
            ));

        SocialUnlinkClientException exception = assertThrows(
            SocialUnlinkClientException.class,
            () -> client.unlink(command("123456789"))
        );

        assertFalse(exception.isRetryable());
        assertEquals("KAKAO_USER_ID_MISMATCH", exception.getErrorCode());
    }

    @Test
    void 카카오_요청_시간초과는_재시도_가능한_네트워크_오류로_변환한다() {
        server.expect(requestTo(UNLINK_URL))
            .andRespond(request -> {
                throw new SocketTimeoutException("read timed out");
            });

        SocialUnlinkClientException exception = assertThrows(
            SocialUnlinkClientException.class,
            () -> client.unlink(command("123456789"))
        );

        assertTrue(exception.isRetryable());
        assertEquals("KAKAO_NETWORK_ERROR", exception.getErrorCode());
    }

    private SocialUnlinkCommand command(String providerSubject) {
        return new SocialUnlinkCommand(
            SocialProvider.KAKAO,
            providerSubject,
            null,
            null
        );
    }

    private SocialUnlinkProperties properties() {
        return new SocialUnlinkProperties(
            ADMIN_KEY,
            "naver-client-id",
            "naver-client-secret"
        );
    }
}
