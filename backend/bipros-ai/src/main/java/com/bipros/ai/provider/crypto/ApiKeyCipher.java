package com.bipros.ai.provider.crypto;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-GCM encryption for LLM provider API keys.
 * KEK is loaded from env {@code BIPROS_AI_KEK} (base64-encoded 32-byte key).
 */
@Slf4j
@Component
public class ApiKeyCipher {

    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;

    @Value("${bipros.ai-orchestrator.kek:}")
    private String kekBase64;

    private SecretKey kek;

    @PostConstruct
    public void init() {
        if (kekBase64 == null || kekBase64.isBlank()) {
            log.warn("BIPROS_AI_KEK is not set; API key encryption will use a random key (data loss on restart).");
            byte[] randomKey = new byte[32];
            new SecureRandom().nextBytes(randomKey);
            this.kek = new SecretKeySpec(randomKey, ALGORITHM);
        } else {
            byte[] decoded = Base64.getDecoder().decode(kekBase64);
            if (decoded.length != 32) {
                throw new IllegalArgumentException("BIPROS_AI_KEK must decode to exactly 32 bytes (256 bits), got " + decoded.length);
            }
            this.kek = new SecretKeySpec(decoded, ALGORITHM);
            log.info("ApiKeyCipher initialized with provided KEK.");
        }
    }

    public EncryptedKey encrypt(String plaintext) {
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            new SecureRandom().nextBytes(iv);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, kek, parameterSpec);

            byte[] cipherText = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return new EncryptedKey(iv, cipherText, 1);
        } catch (Exception e) {
            throw new RuntimeException("Failed to encrypt API key", e);
        }
    }

    public String decrypt(byte[] iv, byte[] cipherText, int keyVersion) {
        if (keyVersion != 1) {
            throw new UnsupportedOperationException("Key version " + keyVersion + " is not supported");
        }
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, kek, parameterSpec);

            byte[] plainText = cipher.doFinal(cipherText);
            return new String(plainText, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Failed to decrypt API key", e);
        }
    }

    public record EncryptedKey(byte[] iv, byte[] ciphertext, int version) {
    }
}
