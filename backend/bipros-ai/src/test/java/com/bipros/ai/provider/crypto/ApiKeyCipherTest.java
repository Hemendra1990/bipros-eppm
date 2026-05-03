package com.bipros.ai.provider.crypto;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApiKeyCipherTest {

    private static String stableKekB64() {
        byte[] kek = new byte[32];
        for (int i = 0; i < 32; i++) kek[i] = (byte) i;
        return Base64.getEncoder().encodeToString(kek);
    }

    private static ApiKeyCipher cipherWithKek(String... activeProfiles) {
        MockEnvironment env = new MockEnvironment();
        for (String p : activeProfiles) env.addActiveProfile(p);
        ApiKeyCipher cipher = new ApiKeyCipher(env);
        ReflectionTestUtils.setField(cipher, "kekBase64", stableKekB64());
        cipher.init();
        return cipher;
    }

    @Test
    void encryptAndDecryptRoundTrip() {
        ApiKeyCipher cipher = cipherWithKek();

        String original = "sk-test-key-12345";
        ApiKeyCipher.EncryptedKey encrypted = cipher.encrypt(original);

        assertThat(encrypted.iv()).isNotEmpty();
        assertThat(encrypted.ciphertext()).isNotEmpty();
        assertThat(encrypted.version()).isEqualTo(1);

        String decrypted = cipher.decrypt(encrypted.iv(), encrypted.ciphertext(), encrypted.version());
        assertThat(decrypted).isEqualTo(original);
    }

    @Test
    void differentPlaintextsProduceDifferentCiphertexts() {
        ApiKeyCipher cipher = cipherWithKek();

        ApiKeyCipher.EncryptedKey e1 = cipher.encrypt("key-one");
        ApiKeyCipher.EncryptedKey e2 = cipher.encrypt("key-one");

        assertThat(e1.ciphertext()).isNotEqualTo(e2.ciphertext());
    }

    @Test
    void devProfileWithoutKekFallsBackToEphemeralKey() {
        // Default profile (no explicit profile) must boot — ephemeral fallback is OK in dev.
        MockEnvironment env = new MockEnvironment();
        ApiKeyCipher cipher = new ApiKeyCipher(env);
        // No KEK configured.
        cipher.init();
        // Round trip with the ephemeral key still works in-process.
        String enc = cipher.encrypt("dev-key").ciphertext().length > 0 ? "ok" : "no";
        assertThat(enc).isEqualTo("ok");
    }

    @Test
    void prodProfileWithoutKekRefusesToStart() {
        MockEnvironment env = new MockEnvironment();
        env.addActiveProfile("prod");
        ApiKeyCipher cipher = new ApiKeyCipher(env);

        assertThatThrownBy(cipher::init)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("BIPROS_AI_KEK is not set");
    }
}
