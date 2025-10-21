package com.example.travel.assistant.api;

import com.example.travel.assistant.service.AgentService;
import com.example.travel.assistant.service.AssistantService;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@RestController
@RequestMapping("/api/assistant")
public class AssistantQueryController {

    public static class Message {
        private final String role; // "user" | "assistant"
        private final String content;

        @JsonCreator
        public Message(@JsonProperty("role") String role,
                       @JsonProperty("content") String content) {
            this.role = role;
            this.content = content;
        }
        public String getRole() { return role; }
        public String getContent() { return content; }
    }

    public static class QueryRequest {
        private final String prompt;
        private final List<Message> history; // optional dialog window
        private final String mode; // "agent" (default) or "llm"
        private final String sessionId; // optional memory id
        private final String userId; // optional active user id for this session

        @JsonCreator
        public QueryRequest(@JsonProperty("prompt") String prompt,
                            @JsonProperty("history") List<Message> history,
                            @JsonProperty("mode") String mode,
                            @JsonProperty("sessionId") String sessionId,
                            @JsonProperty("userId") String userId) {
            this.prompt = prompt;
            this.history = history;
            this.mode = mode;
            this.sessionId = sessionId;
            this.userId = userId;
        }
        public String getPrompt() { return prompt; }
        public List<Message> getHistory() { return history; }
        public String getMode() { return mode; }
        public String getSessionId() { return sessionId; }
        public String getUserId() { return userId; }
    }

    public static class QueryResponse {
        private final String answer;
        public QueryResponse(String answer) { this.answer = answer; }
        public String getAnswer() { return answer; }
    }

    private final AssistantService assistantService;
    private final AgentService agentService;

    public AssistantQueryController(AssistantService assistantService, AgentService agentService) {
        this.assistantService = assistantService;
        this.agentService = agentService;
    }

    @PostMapping("/query")
    public ResponseEntity<QueryResponse> query(@RequestBody QueryRequest request) {
        try {
            boolean useAgent = request.getMode() == null || "agent".equalsIgnoreCase(request.getMode());
            String reply;
            if (useAgent) {
                String sessionId = request.getSessionId() != null && !request.getSessionId().isBlank()
                        ? request.getSessionId()
                        : UUID.randomUUID().toString();
                // Bind provided userId to this session (optional)
                if (request.getUserId() != null && !request.getUserId().isBlank()) {
                    agentService.setActiveUser(sessionId, request.getUserId());
                }
                reply = agentService.ask(sessionId, Objects.toString(request.getPrompt(), ""));
            } else {
                String compiledPrompt = compilePrompt(request); // plain LLM fallback uses client-side history
                reply = assistantService.ask(compiledPrompt);
            }
            return ResponseEntity.ok(new QueryResponse(reply));
        } catch (Exception e) {
            String msg = "Assistant error: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
            return ResponseEntity.ok(new QueryResponse(msg));
        }
    }

    private static String compilePrompt(QueryRequest request) {
        StringBuilder sb = new StringBuilder();
        sb.append("SYSTEM: You operate fully offline. Do not suggest searching the internet or using external websites or apps. Use only internal tools and provided context. If information is not available, ask for missing details or say you don't have that data. Keep answers concise and plain text.\n\n");
        List<Message> history = request.getHistory();
        if (history != null && !history.isEmpty()) {
            sb.append("Previous conversation:\n");
            for (Message m : history) {
                if (m == null || m.getContent() == null) continue;
                String role = m.getRole();
                String label = (role != null && role.equalsIgnoreCase("assistant")) ? "Assistant" : "User";
                sb.append(label).append(": ").append(m.getContent()).append("\n");
            }
            sb.append("---\n");
        }
        sb.append("User: ").append(Objects.toString(request.getPrompt(), ""));
        return sb.toString();
    }
}
