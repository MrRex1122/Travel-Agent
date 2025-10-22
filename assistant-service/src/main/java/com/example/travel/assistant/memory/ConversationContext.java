package com.example.travel.assistant.memory;

/**
 * Simple thread-local holder for current conversation memory id.
 * AgentService sets it before delegating to the agent so tools can
 * access the memory id and write summaries into chat memory.
 */
public final class ConversationContext {

    private ConversationContext() {}

    private static final ThreadLocal<String> MEMORY_ID = new ThreadLocal<>();

    public static void setMemoryId(String memoryId) {
        if (memoryId == null || memoryId.isBlank()) {
            MEMORY_ID.remove();
        } else {
            MEMORY_ID.set(memoryId);
        }
    }

    public static String getMemoryId() {
        return MEMORY_ID.get();
    }

    public static void clear() {
        MEMORY_ID.remove();
    }
}
