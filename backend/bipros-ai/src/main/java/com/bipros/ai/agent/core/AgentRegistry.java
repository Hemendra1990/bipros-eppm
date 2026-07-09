package com.bipros.ai.agent.core;

import com.bipros.common.exception.ResourceNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Registry of all {@link Agent} beans, keyed by {@link Agent#key()}. Spring injects every agent
 * bean at construction; duplicate keys fail fast at boot.
 */
@Slf4j
@Component
public class AgentRegistry {

    private final Map<String, Agent> agents = new LinkedHashMap<>();

    public AgentRegistry(List<Agent> agentBeans) {
        for (Agent a : agentBeans) {
            Agent prev = agents.put(a.key(), a);
            if (prev != null) {
                throw new IllegalStateException("Duplicate agent key '" + a.key() + "' registered by "
                        + prev.getClass().getName() + " and " + a.getClass().getName());
            }
        }
        log.info("AgentRegistry initialised with {} agents: {}", agents.size(), agents.keySet());
    }

    public Agent get(String key) {
        Agent a = agents.get(key);
        if (a == null) {
            throw new ResourceNotFoundException("Agent", key);
        }
        return a;
    }

    public boolean exists(String key) {
        return agents.containsKey(key);
    }

    public Collection<Agent> all() {
        return List.copyOf(agents.values());
    }

    public List<String> keys() {
        return List.copyOf(agents.keySet());
    }
}
