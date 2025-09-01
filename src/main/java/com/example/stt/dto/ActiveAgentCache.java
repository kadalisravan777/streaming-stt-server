package com.example.stt.dto;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import lombok.ToString;

@Component
@ToString
public class ActiveAgentCache {
    private Set<String> activeAgents = ConcurrentHashMap.newKeySet();

    public void updateAgents(List<String> agents) {
        activeAgents.clear();
        activeAgents.addAll(agents);
    }

    public boolean isActive(String agentId) {
        return activeAgents.contains(agentId);
    }
}
