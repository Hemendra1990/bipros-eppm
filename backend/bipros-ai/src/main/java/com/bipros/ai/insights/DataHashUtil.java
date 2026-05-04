package com.bipros.ai.insights;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

@Component
@RequiredArgsConstructor
public class DataHashUtil {

    private final ObjectMapper objectMapper;

    public String computeHash(JsonNode dataSnapshot) {
        try {
            String canonical = objectMapper.writeValueAsString(dataSnapshot);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(canonical.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            throw new RuntimeException("Failed to compute data hash", e);
        }
    }
}
