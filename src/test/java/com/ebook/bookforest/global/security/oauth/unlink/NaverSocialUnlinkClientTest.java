package com.ebook.bookforest.global.security.oauth.unlink;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.ebook.bookforest.domain.user.enums.ProviderTokenType;
import com.ebook.bookforest.domain.user.enums.SocialProvider;
import java.net.SocketTimeoutException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class NaverSocialUnlinkClientTest {

    private static final String REVOKE_URL =
        "https://nid.naver.com/oauth2.0/revoke";
    private static final String CLIENT_ID = "naver-client-id";
    private static final String CLIENT_SECRET = "naver-client-secret";

    private MockRestServiceServer server;
    private NaverSocialUnlinkClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new NaverSocialUnlinkClient(
            builder,
            properties()
        );
    }

    @AfterEach
    void verifyRequests() {
        server.verify();
    }

    @Test
    void 네이버_Refresh_Token과_Client_인증정보로_토큰을_폐기한다() {
        server.expect(requestTo(REVOKE_URL))
            .andExpect(method(HttpMethod.POST))
            .andExpect(content().contentTypeCompatibleWith(
                MediaType.APPLICATION_FORM_URLENCODED
            ))
            .andExpect(content().string(
                "client_id=naver-client-id"
                    + "&client_secret=naver-client-secret"
                    + "&token=naver-refresh-token"
                    + "&token_type_hint=refresh_token"
            ))
            .andRespond(withSuccess());

        client.unlink(command(
            "naver-refresh-token",
            ProviderTokenType.REFRESH_TOKEN
        ));
    }

    @Test
    void Access_Token은_access_token_힌트로_전송한다() {
        server.expect(requestTo(REVOKE_URL))
            .andExpect(content().string(
                "client_id=naver-client-id"
                    + "&client_secret=naver-client-secret"
                    + "&token=naver-access-token"
                    + "&token_type_hint=access_token"
            ))
            .andRespond(withSuccess());

        client.unlink(command(
            "naver-access-token",
            ProviderTokenType.ACCESS_TOKEN
        ));
    }

    @Test
    void 네이버_503_응답은_재시도_가능한_오류로_변환한다() {
        server.expect(requestTo(REVOKE_URL))
            .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        SocialUnlinkClientException exception = assertThrows(
            SocialUnlinkClientException.class,
            () -> client.unlink(command(
                "naver-refresh-token",
                ProviderTokenType.REFRESH_TOKEN
            ))
        );

        assertTrue(exception.isRetryable());
        assertEquals(
            "NAVER_TEMPORARILY_UNAVAILABLE",
            exception.getErrorCode()
        );
    }

    @Test
    void 네이버_401_응답은_영구_오류로_변환한다() {
        server.expect(requestTo(REVOKE_URL))
            .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        SocialUnlinkClientException exception = assertThrows(
            SocialUnlinkClientException.class,
            () -> client.unlink(command(
                "naver-refresh-token",
                ProviderTokenType.REFRESH_TOKEN
            ))
        );

        assertFalse(exception.isRetryable());
        assertEquals("NAVER_UNAUTHORIZED_CLIENT", exception.getErrorCode());
    }

    @Test
    void 네이버_요청_시간초과는_재시도_가능한_네트워크_오류로_변환한다() {
        server.expect(requestTo(REVOKE_URL))
            .andRespond(request -> {
                throw new SocketTimeoutException("read timed out");
            });

        SocialUnlinkClientException exception = assertThrows(
            SocialUnlinkClientException.class,
            () -> client.unlink(command(
                "naver-refresh-token",
                ProviderTokenType.REFRESH_TOKEN
            ))
        );

        assertTrue(exception.isRetryable());
        assertEquals("NAVER_NETWORK_ERROR", exception.getErrorCode());
    }

    private SocialUnlinkCommand command(
        String token,
        ProviderTokenType tokenType
    ) {
        return new SocialUnlinkCommand(
            SocialProvider.NAVER,
            "naver-user-id",
            token,
            tokenType
        );
    }

    private SocialUnlinkProperties properties() {
        return new SocialUnlinkProperties(
            "kakao-admin-key",
            CLIENT_ID,
            CLIENT_SECRET
        );
    }
}
