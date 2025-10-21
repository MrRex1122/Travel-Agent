package com.example.travel.assistant.memory;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Shared memory registry storing per-session MessageWindowChatMemory instances
 * and exposing simple debug helpers to inspect/clear memory.
 */
public class SharedChatMemoryProvider {

    private final ConcurrentHashMap<String, MessageWindowChatMemory> memories = new ConcurrentHashMap<>();
    private final int maxMessages;

    public SharedChatMemoryProvider() { this(20); }

    public SharedChatMemoryProvider(int maxMessages) { this.maxMessages = maxMessages; }

    public MessageWindowChatMemory get(Object memoryId) {
        String key = memoryId != null ? memoryId.toString() : "default";
        return memories.computeIfAbsent(key, k -> MessageWindowChatMemory.withMaxMessages(maxMessages));
    }

    public Set<String> keys() { return new TreeSet<>(memories.keySet()); }

    public List<Map<String, Object>> dump(String sessionId) {
        MessageWindowChatMemory m = memories.get(sessionId != null ? sessionId : "default");
        if (m == null) return List.of();
        return m.messages().stream().map(SharedChatMemoryProvider::toMap).collect(Collectors.toList());
    }

    public void clear(String sessionId) {
        MessageWindowChatMemory m = memories.get(sessionId != null ? sessionId : "default");
        if (m != null) m.clear();
    }

    private static Map<String, Object> toMap(ChatMessage msg) {
        Map<String, Object> map = new LinkedHashMap<>();
        if (msg instanceof UserMessage um) {
            map.put("type", "user");
            map.put("content", um.text());
        } else if (msg instanceof AiMessage am) {
            map.put("type", "assistant");
            map.put("content", am.text());
        } else if (msg instanceof SystemMessage sm) {
            map.put("type", "system");
            map.put("content", sm.text());
        } else {
            map.put("type", msg.getClass().getSimpleName());
            map.put("content", String.valueOf(msg));
        }
        return map;
    }
}
