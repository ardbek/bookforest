package com.ebook.bookforest.global.security.crypto;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

@Component
public class AesGcmOAuthTokenCipher implements OAuthTokenCipher {

    private static final String ALGORITHM = "AES/GCM/NoPadding";

    // 암호문 형식 버전
    private static final String VERSION = "v1";
    private static final String PREFIX = VERSION + ":";

    // GCM 권장 IV 길이: 12바이트
    private static final int IV_LENGTH_BYTES = 12;

    // 인증 태그 길이: 128비트
    private static final int TAG_LENGTH_BITS = 128;

    // AES-256 키 길이: 32바이트
    private static final int KEY_LENGTH_BYTES = 32;

    private final SecretKeySpec secretKey;
    private final SecureRandom secureRandom;

    public AesGcmOAuthTokenCipher(SocialTokenCryptoProperties properties) {
        this.secretKey = createSecretKey(properties.encryptionKey());
        this.secureRandom = new SecureRandom();
    }

    @Override
    public String encrypt(String plainToken) {
        validatePlainToken(plainToken);

        try {
            byte[] iv = new byte[IV_LENGTH_BYTES];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);

            GCMParameterSpec parameterSpec = new GCMParameterSpec(TAG_LENGTH_BITS, iv);

            cipher.init(Cipher.ENCRYPT_MODE, secretKey, parameterSpec);

            /*
             * 암호화 버전도 무결성 검증 대상에 포함
             */
            cipher.updateAAD(VERSION.getBytes(StandardCharsets.UTF_8));

            byte[] encryptedToken = cipher.doFinal(plainToken.getBytes(StandardCharsets.UTF_8));

            byte[] result = ByteBuffer
                .allocate(iv.length + encryptedToken.length)
                .put(iv)
                .put(encryptedToken)
                .array();

            return PREFIX + Base64.getEncoder().encodeToString(result);

        } catch (GeneralSecurityException exception) {
            throw new OAuthTokenCryptoException("소셜 토큰 암호화에 실패했습니다.", exception);
        }
    }

    @Override
    public String decrypt(String ciphertext) {
        validateCiphertext(ciphertext);

        try {
            String encodedValue = ciphertext.substring(PREFIX.length());

            byte[] decodedValue = Base64.getDecoder().decode(encodedValue);

            /*
             * IV 외에도 최소 한 바이트 이상의 암호문이 존재해야 함
             */
            if (decodedValue.length <= IV_LENGTH_BYTES) {
                throw new OAuthTokenCryptoException("소셜 토큰 암호문의 길이가 올바르지 않습니다.");
            }

            byte[] iv = Arrays.copyOfRange(decodedValue, 0, IV_LENGTH_BYTES);

            byte[] encryptedToken = Arrays.copyOfRange(decodedValue, IV_LENGTH_BYTES, decodedValue.length);

            Cipher cipher = Cipher.getInstance(ALGORITHM);

            GCMParameterSpec parameterSpec = new GCMParameterSpec(TAG_LENGTH_BITS, iv);

            cipher.init(Cipher.DECRYPT_MODE, secretKey, parameterSpec);

            cipher.updateAAD(VERSION.getBytes(StandardCharsets.UTF_8));

            byte[] plainToken = cipher.doFinal(encryptedToken);

            return new String(plainToken, StandardCharsets.UTF_8);

        } catch (OAuthTokenCryptoException exception) {
            throw exception;

        } catch (GeneralSecurityException | IllegalArgumentException exception) {
            /*
             * 잘못된 키, 변조된 암호문, 잘못된 Base64 모두
             * 동일한 애플리케이션 예외로 변환
             */
            throw new OAuthTokenCryptoException("소셜 토큰 복호화에 실패했습니다.", exception);
        }
    }

    private SecretKeySpec createSecretKey(String encodedKey) {
        final byte[] decodedKey;

        try {
            decodedKey = Base64.getDecoder().decode(encodedKey);

        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("소셜 토큰 암호화 키는 Base64 형식이어야 합니다.", exception);
        }

        if (decodedKey.length != KEY_LENGTH_BYTES) {
            throw new IllegalArgumentException("소셜 토큰 암호화 키는 32바이트여야 합니다.");
        }

        return new SecretKeySpec(decodedKey, "AES");
    }

    private void validatePlainToken(String plainToken) {
        if (plainToken == null || plainToken.isBlank()) {
            throw new IllegalArgumentException("암호화할 소셜 토큰이 비어 있습니다.");
        }
    }

    private void validateCiphertext(String ciphertext) {
        if (ciphertext == null || ciphertext.isBlank()) {
            throw new IllegalArgumentException("복호화할 소셜 토큰이 비어 있습니다.");
        }

        if (!ciphertext.startsWith(PREFIX)) {
            throw new OAuthTokenCryptoException("지원하지 않는 소셜 토큰 암호화 버전입니다.");
        }
    }
}