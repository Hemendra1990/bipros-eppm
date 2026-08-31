package com.bipros.api.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "bipros.whatsapp")
public class WhatsAppProperties {

    private String frontendUrl = "http://localhost:3000";
}
