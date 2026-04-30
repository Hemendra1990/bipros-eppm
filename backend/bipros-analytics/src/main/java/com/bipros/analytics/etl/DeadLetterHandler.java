package com.bipros.analytics.etl;

import com.bipros.analytics.etl.watermark.EtlDeadLetter;
import com.bipros.analytics.etl.watermark.EtlDeadLetterRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class DeadLetterHandler {

    private final EtlDeadLetterRepository deadLetterRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public void record(String sourceTable, String targetTable, Object payload, Exception error) {
        try {
            EtlDeadLetter dl = new EtlDeadLetter();
            dl.setPayload(objectMapper.writeValueAsString(Map.of(
                    "sourceTable", sourceTable,
                    "targetTable", targetTable,
                    "payload", payload
            )));
            dl.setError(error.getMessage());
            dl.setAttempts(1);
            dl.setNextRetryAt(Instant.now().plusSeconds(300));
            deadLetterRepository.save(dl);
            log.warn("ETL dead-letter recorded: {} -> {} error={}", sourceTable, targetTable, error.getMessage());
        } catch (Exception e) {
            log.error("Failed to write dead-letter for {} -> {}", sourceTable, targetTable, e);
        }
    }
}
