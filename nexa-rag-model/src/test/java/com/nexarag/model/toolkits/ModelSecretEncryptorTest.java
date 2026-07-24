package com.nexarag.model.toolkits;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 模型密钥加密器测试。
 */
class ModelSecretEncryptorTest {

    @Test
    void shouldEncryptDecryptAndMaskApiKey() {
        ModelSecretEncryptor encryptor = new ModelSecretEncryptor("0123456789abcdef0123456789abcdef");

        String cipher = encryptor.encrypt("sk-test-abcdef");
        String raw = encryptor.decrypt(cipher);
        String mask = encryptor.mask("sk-test-abcdef");

        assertThat(cipher).isNotEqualTo("sk-test-abcdef");
        assertThat(raw).isEqualTo("sk-test-abcdef");
        assertThat(mask).isEqualTo("sk-****cdef");
    }

    @Test
    void shouldAcceptBase64EncodedMasterKey() {
        ModelSecretEncryptor encryptor = new ModelSecretEncryptor("=");

        String cipher = encryptor.encrypt("sk-test-abcdef");
        String raw = encryptor.decrypt(cipher);

        assertThat(raw).isEqualTo("sk-test-abcdef");
    }

    @Test
    void shouldRejectBlankMasterKey() {
        assertThatThrownBy(() -> new ModelSecretEncryptor(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("模型密钥主密钥不能为空");
    }
}
