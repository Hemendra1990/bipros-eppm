package com.bipros.ai.insights;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
public class InsightsGenerationLock {

    private static final ConcurrentHashMap<String, Object> LOCKS = new ConcurrentHashMap<>();

    public Object acquire(UUID projectId, String tab) {
        String key = projectId + ":" + tab;
        return LOCKS.computeIfAbsent(key, k -> new Object());
    }

    public void release(UUID projectId, String tab) {
        String key = projectId + ":" + tab;
        LOCKS.remove(key);
    }
}
