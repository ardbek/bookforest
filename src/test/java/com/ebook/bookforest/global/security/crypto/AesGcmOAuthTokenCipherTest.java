package com.ebook.bookforest.global.security.crypto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

        assertNotEquals(PLAIN_TOKEN, ciphertext);
        assertTrue(ciphertext.startsWith(VERSION_PREFIX));
        assertEquals(PLAIN_TOKEN, decryptedToken);
    }

    @Test
    void 같은_토큰을_두번_암호화해도_랜덤_IV로_암호문이_달라진다() {
        String firstCiphertext = tokenCipher.encrypt(PLAIN_TOKEN);
        String secondCiphertext = tokenCipher.encrypt(PLAIN_TOKEN);

        assertNotEquals(firstCiphertext, secondCiphertext);
        assertEquals(PLAIN_TOKEN, tokenCipher.decrypt(firstCiphertext));
        assertEquals(PLAIN_TOKEN, tokenCipher.decrypt(secondCiphertext));
    }

    @Test
    void 암호문이_변조되면_복호화할_수_없다() {
        String ciphertext = tokenCipher.encrypt(PLAIN_TOKEN);
        byte[] encryptedBytes = decodePayload(ciphertext);

        encryptedBytes[encryptedBytes.length - 1] ^= 1;
        String tamperedCiphertext = VERSION_PREFIX
            + Base64.getEncoder().encodeToString(encryptedBytes);

        assertThrows(
            OAuthTokenCryptoException.class,
            () -> tokenCipher.decrypt(tamperedCiphertext)
        );
    }

    @Test
    void 다른_암호화_키로는_복호화할_수_없다() {
        String ciphertext = tokenCipher.encrypt(PLAIN_TOKEN);
        OAuthTokenCipher differentKeyCipher = createTokenCipher((byte) 8);

        assertThrows(
            OAuthTokenCryptoException.class,
            () -> differentKeyCipher.decrypt(ciphertext)
        );
    }

    @Test
    void 지원하지_않는_암호화_버전은_복호화할_수_없다() {
        assertThrows(
            OAuthTokenCryptoException.class,
            () -> tokenCipher.decrypt("v2:invalid-ciphertext")
        );
    }

    @Test
    void Base64가_아닌_암호문은_복호화할_수_없다() {
        assertThrows(
            OAuthTokenCryptoException.class,
            () -> tokenCipher.decrypt("v1:not-valid-base64!!!")
        );
    }

    @Test
    void IV보다_짧은_암호문은_복호화할_수_없다() {
        String tooShortCiphertext = VERSION_PREFIX
            + Base64.getEncoder().encodeToString(new byte[12]);

        assertThrows(
            OAuthTokenCryptoException.class,
            () -> tokenCipher.decrypt(tooShortCiphertext)
        );
    }

    @Test
    void 빈_토큰은_암호화하거나_복호화할_수_없다() {
        assertThrows(
            IllegalArgumentException.class,
            () -> tokenCipher.encrypt(null)
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> tokenCipher.encrypt(" ")
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> tokenCipher.decrypt(null)
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> tokenCipher.decrypt(" ")
        );
    }

    @Test
    void 암호화_키가_Base64_형식이_아니면_생성할_수_없다() {
        SocialTokenCryptoProperties properties =
            new SocialTokenCryptoProperties("not-valid-base64!!!");

        assertThrows(
            IllegalArgumentException.class,
            () -> new AesGcmOAuthTokenCipher(properties)
        );
    }

    @Test
    void 암호화_키가_32바이트가_아니면_생성할_수_없다() {
        String shortKey = Base64.getEncoder()
            .encodeToString(new byte[16]);
        SocialTokenCryptoProperties properties =
            new SocialTokenCryptoProperties(shortKey);

        assertThrows(
            IllegalArgumentException.class,
            () -> new AesGcmOAuthTokenCipher(properties)
        );
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
