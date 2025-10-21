package com.example.travel.assistant.api;

import com.example.travel.assistant.memory.SharedChatMemoryProvider;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/assistant/memory")
public class MemoryDebugController {

    private final SharedChatMemoryProvider registry;

    public MemoryDebugController(SharedChatMemoryProvider registry) {
        this.registry = registry;
    }

    @GetMapping
    public Map<String, Object> listKeys() {
        Map<String, Object> resp = new HashMap<>();
        resp.put("keys", registry.keys());
        return resp;
    }

    @GetMapping("/{sessionId}")
    public Map<String, Object> read(@PathVariable String sessionId) {
        var msgs = registry.dump(sessionId);
        Map<String, Object> resp = new HashMap<>();
        resp.put("sessionId", sessionId);
        resp.put("size", msgs.size());
        resp.put("messages", msgs);
        return resp;
    }

    @DeleteMapping("/{sessionId}")
    public ResponseEntity<Void> clear(@PathVariable String sessionId) {
        registry.clear(sessionId);
        return ResponseEntity.noContent().build();
    }
}
