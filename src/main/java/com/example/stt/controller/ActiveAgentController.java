package com.example.stt.controller;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.stt.dto.ActiveAgentCache;
import com.example.stt.dto.ActiveAgentRequest;

@RestController
@RequestMapping("/api")
public class ActiveAgentController {

    @Autowired
    private ActiveAgentCache agentCache;

    @PostMapping("/active-agents")
    public ResponseEntity<String> updateActiveAgents(@RequestBody ActiveAgentRequest activeAgentRequest) {
        List<String> agents = activeAgentRequest.getActiveAgents();
        if (agents != null) {
            agentCache.updateAgents(agents);
            return ResponseEntity.ok("Active agents updated");
        } else {
            return ResponseEntity.badRequest().body("Missing activeAgents key");
        }
    }
}
