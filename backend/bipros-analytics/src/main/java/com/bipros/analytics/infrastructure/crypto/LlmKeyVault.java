package com.bipros.analytics.infrastructure.crypto;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.HexFormat;

/**
 * AES-256-GCM envelope encryption for BYOK API keys at rest.
 *
 * Layout: ciphertext = IV(12) || ENCRYPTED_PAYLOAD(plaintext + 16-byte GCM tag).
 * The KEK is a 32-byte master sourced from {@code bipros.analytics.llm.kek-hex}
 * (typically env var {@code BIPROS_LLM_KEK_HEX}). In dev a deterministic local
 * key is used with a loud warning, mirroring the JWT_SECRET fallback pattern.
 */
@Component
@Slf4j
public class LlmKeyVault {

    private static final String ALGO = "AES/GCM/NoPadding";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final SecureRandom RNG = new SecureRandom();

    // Deterministic dev fallback. 64 hex chars = 32 bytes. NEVER use in prod.
    private static final String DEV_FALLBACK_HEX =
        "babe0ff1ce5ca1ab1ed00ff5ee6abe0fdeadbeefcafef00dba5eba11feeddeed";

    private final SecretKey kek;

    public LlmKeyVault(@Value("${bipros.analytics.llm.kek-hex:}") String kekHex) {
        String resolved = kekHex == null || kekHex.isBlank() ? null : kekHex;
        if (resolved == null) {
            log.warn("BIPROS_LLM_KEK_HEX is not set. Using a dev fallback KEK. " +
                     "DO NOT use this in production — set bipros.analytics.llm.kek-hex / BIPROS_LLM_KEK_HEX.");
            resolved = DEV_FALLBACK_HEX;
        }
        if (resolved.length() != 64) {
            throw new IllegalArgumentException(
                "BIPROS_LLM_KEK_HEX must be 64 hex characters (32 bytes). Got " + resolved.length());
        }
        byte[] raw = HexFormat.of().parseHex(resolved);
        this.kek = new SecretKeySpec(raw, "AES");
    }

    public byte[] encrypt(String plaintext) {
        try {
            byte[] iv = new byte[IV_BYTES];
            RNG.nextBytes(iv);
            Cipher c = Cipher.getInstance(ALGO);
            c.init(Cipher.ENCRYPT_MODE, kek, new GCMParameterSpec(TAG_BITS, iv));
            byte[] payload = c.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] out = new byte[IV_BYTES + payload.length];
            System.arraycopy(iv, 0, out, 0, IV_BYTES);
            System.arraycopy(payload, 0, out, IV_BYTES, payload.length);
            return out;
        } catch (Exception e) {
            throw new IllegalStateException("LLM key encrypt failed", e);
        }
    }

    public String decrypt(byte[] ciphertext) {
        try {
            byte[] iv = new byte[IV_BYTES];
            System.arraycopy(ciphertext, 0, iv, 0, IV_BYTES);
            Cipher c = Cipher.getInstance(ALGO);
            c.init(Cipher.DECRYPT_MODE, kek, new GCMParameterSpec(TAG_BITS, iv));
            byte[] plain = c.doFinal(ciphertext, IV_BYTES, ciphertext.length - IV_BYTES);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("LLM key decrypt failed", e);
        }
    }
}
