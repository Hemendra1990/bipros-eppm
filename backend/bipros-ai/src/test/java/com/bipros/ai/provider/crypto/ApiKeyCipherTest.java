package com.bipros.ai.provider.crypto;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

class ApiKeyCipherTest {

    @Test
    void encryptAndDecryptRoundTrip() {
        ApiKeyCipher cipher = new ApiKeyCipher();
        byte[] kek = new byte[32];
        for (int i = 0; i < 32; i++) kek[i] = (byte) i;
        ReflectionTestUtils.setField(cipher, "kekBase64", Base64.getEncoder().encodeToString(kek));
        cipher.init();

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
        ApiKeyCipher cipher = new ApiKeyCipher();
        byte[] kek = new byte[32];
        for (int i = 0; i < 32; i++) kek[i] = (byte) i;
        ReflectionTestUtils.setField(cipher, "kekBase64", Base64.getEncoder().encodeToString(kek));
        cipher.init();

        ApiKeyCipher.EncryptedKey e1 = cipher.encrypt("key-one");
        ApiKeyCipher.EncryptedKey e2 = cipher.encrypt("key-one");

        assertThat(e1.ciphertext()).isNotEqualTo(e2.ciphertext());
    }
}
