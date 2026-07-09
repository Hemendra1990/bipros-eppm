package com.bipros.ai.agent.notify;

import com.bipros.ai.agent.domain.AgentChannelConfig;
import com.bipros.ai.agent.domain.AgentChannelConfigRepository;
import com.bipros.ai.provider.crypto.ApiKeyCipher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

/**
 * Twilio-style messaging provider. Config lives in {@code AgentChannelConfig} (one row per channel
 * key): {@code apiUrl}, {@code accountSid}, an AES-GCM-encrypted {@code authToken} (decrypted with
 * the same {@link ApiKeyCipher} / {@code BIPROS_AI_KEK} scheme as LLM keys), and {@code fromNumber}.
 * The request is a form-encoded POST with HTTP Basic auth (accountSid:authToken). WhatsApp numbers
 * are prefixed {@code whatsapp:}. Every failure path returns false — never throws.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TwilioMessagingProviderAdapter implements MessagingProviderAdapter {

    private static final String WHATSAPP = "whatsapp";
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private final AgentChannelConfigRepository channelConfigRepository;
    private final ApiKeyCipher apiKeyCipher;

    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();

    @Override
    public boolean isConfigured(String channelKey) {
        return channelConfigRepository.findByChannelKey(channelKey)
                .map(c -> c.isActive()
                        && notBlank(c.getApiUrl())
                        && notBlank(c.getAccountSid())
                        && c.getAuthTokenCiphertext() != null
                        && notBlank(c.getFromNumber()))
                .orElse(false);
    }

    @Override
    public boolean send(String channelKey, String toPhone, String message) {
        if (toPhone == null || toPhone.isBlank()) {
            return false;
        }
        AgentChannelConfig cfg = channelConfigRepository.findByChannelKey(channelKey).orElse(null);
        if (cfg == null || !cfg.isActive() || !notBlank(cfg.getApiUrl())) {
            return false;
        }
        try {
            int version = cfg.getAuthTokenVersion() == null ? 1 : cfg.getAuthTokenVersion();
            String token = apiKeyCipher.decrypt(cfg.getAuthTokenIv(), cfg.getAuthTokenCiphertext(), version);

            boolean whatsapp = WHATSAPP.equals(channelKey);
            String from = whatsapp ? "whatsapp:" + cfg.getFromNumber() : cfg.getFromNumber();
            String to = whatsapp ? "whatsapp:" + toPhone : toPhone;
            String form = "From=" + enc(from) + "&To=" + enc(to) + "&Body=" + enc(message);

            String basic = Base64.getEncoder().encodeToString(
                    (cfg.getAccountSid() + ":" + token).getBytes(StandardCharsets.UTF_8));

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(cfg.getApiUrl()))
                    .timeout(TIMEOUT)
                    .header("Authorization", "Basic " + basic)
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(form))
                    .build();

            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            boolean ok = resp.statusCode() / 100 == 2;
            if (!ok) {
                log.warn("Messaging provider {} returned {}: {}",
                        channelKey, resp.statusCode(), truncate(resp.body()));
            }
            return ok;
        } catch (Exception ex) {
            log.warn("Messaging provider {} send failed: {}", channelKey, ex.getMessage());
            return false;
        }
    }

    private static String enc(String s) {
        return URLEncoder.encode(s == null ? "" : s, StandardCharsets.UTF_8);
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private static String truncate(String s) {
        if (s == null) {
            return "";
        }
        return s.length() <= 300 ? s : s.substring(0, 300);
    }
}
