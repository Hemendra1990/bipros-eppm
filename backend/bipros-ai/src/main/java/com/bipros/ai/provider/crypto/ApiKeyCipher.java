package com.bipros.ai.provider.crypto;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashSet;
import java.util.Set;

/**
 * AES-GCM encryption for LLM provider API keys.
 * KEK is loaded from env {@code BIPROS_AI_KEK} (base64-encoded 32-byte key).
 *
 * <p>If the KEK is missing in a non-dev profile, startup fails fast — the only
 * outcomes that aren't a fail-fast are: (a) explicit local development with a
 * dev/local/test profile active, or (b) the operator has provided a stable key.
 * The earlier silent-ephemeral-key behavior caused a real incident: an API key
 * persisted in one boot couldn't be decrypted in the next, surfacing as an
 * obscure {@code AEADBadTagException} thirty seconds into a user request.
 */
@Slf4j
@Component
public class ApiKeyCipher {

    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;

    private static final Set<String> DEV_PROFILES = new HashSet<>(Arrays.asList(
            "dev", "local", "test", "default", "seed"
    ));

    @Value("${bipros.ai-orchestrator.kek:}")
    private String kekBase64;

    private final Environment environment;

    private SecretKey kek;

    public ApiKeyCipher(Environment environment) {
        this.environment = environment;
    }

    @PostConstruct
    public void init() {
        if (kekBase64 != null && !kekBase64.isBlank()) {
            byte[] decoded = Base64.getDecoder().decode(kekBase64);
            if (decoded.length != 32) {
                throw new IllegalArgumentException(
                        "BIPROS_AI_KEK must decode to exactly 32 bytes (256 bits), got " + decoded.length);
            }
            this.kek = new SecretKeySpec(decoded, ALGORITHM);
            log.info("ApiKeyCipher initialized with provided KEK.");
            return;
        }

        if (!isDevProfile()) {
            throw new IllegalStateException(
                    "BIPROS_AI_KEK is not set. The application refuses to start in a non-dev " +
                    "profile without a stable Key Encryption Key, because any LLM provider API " +
                    "keys persisted under an ephemeral key would be unrecoverable on restart. " +
                    "Set BIPROS_AI_KEK to a base64-encoded 32-byte value, or run with " +
                    "--spring.profiles.active=dev for local development.");
        }

        log.warn("BIPROS_AI_KEK is not set; falling back to an ephemeral random key for the " +
                "current dev profile. Any API key saved this run becomes UNRECOVERABLE on restart.");
        byte[] randomKey = new byte[32];
        new SecureRandom().nextBytes(randomKey);
        this.kek = new SecretKeySpec(randomKey, ALGORITHM);
    }

    private boolean isDevProfile() {
        String[] active = environment.getActiveProfiles();
        if (active.length == 0) {
            // No explicit profile = treat as dev (Spring's "default" semantics).
            return true;
        }
        for (String p : active) {
            if (DEV_PROFILES.contains(p.toLowerCase())) return true;
        }
        return false;
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
