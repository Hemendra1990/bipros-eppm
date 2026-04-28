package com.bipros.analytics.infrastructure.crypto;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LlmKeyVaultTest {

    private static final String HEX_KEY_A = "0".repeat(64);
    private static final String HEX_KEY_B = "1".repeat(64);

    @Test
    void encryptThenDecrypt_roundTrips() {
        LlmKeyVault vault = new LlmKeyVault(HEX_KEY_A);
        byte[] cipher = vault.encrypt("sk-ant-api03-secret");
        assertThat(vault.decrypt(cipher)).isEqualTo("sk-ant-api03-secret");
    }

    @Test
    void encrypt_producesDifferentCiphertextEachCall_dueToRandomIv() {
        LlmKeyVault vault = new LlmKeyVault(HEX_KEY_A);
        assertThat(vault.encrypt("same-input")).isNotEqualTo(vault.encrypt("same-input"));
    }

    @Test
    void decrypt_withWrongKey_throws() {
        byte[] cipher = new LlmKeyVault(HEX_KEY_A).encrypt("secret");
        assertThatThrownBy(() -> new LlmKeyVault(HEX_KEY_B).decrypt(cipher))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void constructor_rejectsKeyOfWrongLength() {
        assertThatThrownBy(() -> new LlmKeyVault("deadbeef"))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
