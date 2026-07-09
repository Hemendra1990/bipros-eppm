package com.bipros.ai.agent.domain;

import com.bipros.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Configuration for an outbound messaging channel (whatsapp | sms). Email uses Spring Mail props;
 * in-app needs no config. The provider auth token is encrypted at rest with the same
 * {@code ApiKeyCipher} / {@code BIPROS_AI_KEK} scheme used for LLM provider keys.
 */
@Entity
@Table(schema = "ai", name = "agent_channel_config")
@Getter
@Setter
public class AgentChannelConfig extends BaseEntity {

    @Column(name = "channel_key", nullable = false, unique = true, length = 20)
    private String channelKey;

    @Column(name = "api_url", length = 500)
    private String apiUrl;

    @Column(name = "account_sid", length = 200)
    private String accountSid;

    @Column(name = "auth_token_ciphertext")
    private byte[] authTokenCiphertext;

    @Column(name = "auth_token_iv")
    private byte[] authTokenIv;

    @Column(name = "auth_token_version")
    private Integer authTokenVersion;

    @Column(name = "from_number", length = 40)
    private String fromNumber;

    @Column(nullable = false)
    private boolean active = false;
}
