package com.ebook.bookforest.domain.user.crypto;


import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ebook.bookforest.domain.user.config.SocialTokenCryptoProperties;
import java.util.Arrays;
import java.util.Base64;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AesGcmOAuthTokenCipherTest {

    private static final String VERSION_PREFIX = "v1:";
    private static final String PLAIN_TOKEN = "naver-refresh-token-value";

    private OAuthTokenCipher tokenCipher;

    @BeforeEach
    void setUp() {
        tokenCipher = createTokenCipher((byte) 7);
    }

    @Test
    void 암호화한_토큰을_복호화하면_원본과_같다() {
        String ciphertext = tokenCipher.encrypt(PLAIN_TOKEN);

        String decryptedToken = tokenCipher.decrypt(ciphertext);

        assertThat(ciphertext).isNotEqualTo(PLAIN_TOKEN);
        assertThat(ciphertext).startsWith(VERSION_PREFIX);
        assertThat(decryptedToken).isEqualTo(PLAIN_TOKEN);
    }

    @Test
    void 같은_토큰을_두번_암호화해도_랜덤_IV로_암호문이_달라진다() {
        String firstCiphertext = tokenCipher.encrypt(PLAIN_TOKEN);
        String secondCiphertext = tokenCipher.encrypt(PLAIN_TOKEN);

        assertThat(secondCiphertext).isNotEqualTo(firstCiphertext);
        assertThat(tokenCipher.decrypt(firstCiphertext)).isEqualTo(PLAIN_TOKEN);
        assertThat(tokenCipher.decrypt(secondCiphertext)).isEqualTo(PLAIN_TOKEN);
    }

    @Test
    void 암호문이_변조되면_복호화할_수_없다() {
        String ciphertext = tokenCipher.encrypt(PLAIN_TOKEN);
        byte[] encryptedBytes = decodePayload(ciphertext);

        encryptedBytes[encryptedBytes.length - 1] ^= 1;
        String tamperedCiphertext = VERSION_PREFIX
            + Base64.getEncoder().encodeToString(encryptedBytes);

        assertThatThrownBy(() -> tokenCipher.decrypt(tamperedCiphertext)).isInstanceOf(OAuthTokenCryptoException.class);
    }

    @Test
    void 다른_암호화_키로는_복호화할_수_없다() {
        String ciphertext = tokenCipher.encrypt(PLAIN_TOKEN);
        OAuthTokenCipher differentKeyCipher = createTokenCipher((byte) 8);

        assertThatThrownBy(() -> differentKeyCipher.decrypt(ciphertext)).isInstanceOf(OAuthTokenCryptoException.class);
    }

    @Test
    void 지원하지_않는_암호화_버전은_복호화할_수_없다() {
        assertThatThrownBy(() -> tokenCipher.decrypt("v2:invalid-ciphertext"))
            .isInstanceOf(OAuthTokenCryptoException.class);
    }

    @Test
    void Base64가_아닌_암호문은_복호화할_수_없다() {
        assertThatThrownBy(() -> tokenCipher.decrypt("v1:not-valid-base64!!!"))
            .isInstanceOf(OAuthTokenCryptoException.class);
    }

    @Test
    void IV보다_짧은_암호문은_복호화할_수_없다() {
        String tooShortCiphertext = VERSION_PREFIX
            + Base64.getEncoder().encodeToString(new byte[12]);

        assertThatThrownBy(() -> tokenCipher.decrypt(tooShortCiphertext)).isInstanceOf(OAuthTokenCryptoException.class);
    }

    @Test
    void 빈_토큰은_암호화하거나_복호화할_수_없다() {
        assertThatThrownBy(() -> tokenCipher.encrypt(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> tokenCipher.encrypt(" ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> tokenCipher.decrypt(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> tokenCipher.decrypt(" ")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 암호화_키가_Base64_형식이_아니면_생성할_수_없다() {
        SocialTokenCryptoProperties properties =
            new SocialTokenCryptoProperties("not-valid-base64!!!");

        assertThatThrownBy(() -> new AesGcmOAuthTokenCipher(properties)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 암호화_키가_32바이트가_아니면_생성할_수_없다() {
        String shortKey = Base64.getEncoder()
            .encodeToString(new byte[16]);
        SocialTokenCryptoProperties properties =
            new SocialTokenCryptoProperties(shortKey);

        assertThatThrownBy(() -> new AesGcmOAuthTokenCipher(properties)).isInstanceOf(IllegalArgumentException.class);
    }

    private OAuthTokenCipher createTokenCipher(byte keyValue) {
        byte[] key = new byte[32];
        Arrays.fill(key, keyValue);

        String encodedKey = Base64.getEncoder().encodeToString(key);
        SocialTokenCryptoProperties properties =
            new SocialTokenCryptoProperties(encodedKey);

        return new AesGcmOAuthTokenCipher(properties);
    }

    private byte[] decodePayload(String ciphertext) {
        String encodedPayload = ciphertext.substring(VERSION_PREFIX.length());
        return Base64.getDecoder().decode(encodedPayload);
    }
}
