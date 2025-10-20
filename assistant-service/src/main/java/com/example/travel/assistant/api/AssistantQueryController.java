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

        @JsonCreator
        public QueryRequest(@JsonProperty("prompt") String prompt,
                            @JsonProperty("history") List<Message> history,
                            @JsonProperty("mode") String mode) {
            this.prompt = prompt;
            this.history = history;
            this.mode = mode;
        }
        public String getPrompt() { return prompt; }
        public List<Message> getHistory() { return history; }
        public String getMode() { return mode; }
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
            String compiledPrompt = compilePrompt(request);
            boolean useAgent = request.getMode() == null || "agent".equalsIgnoreCase(request.getMode());
            String reply = useAgent ? agentService.ask(compiledPrompt) : assistantService.ask(compiledPrompt);
            return ResponseEntity.ok(new QueryResponse(reply));
        } catch (Exception e) {
            String msg = "Assistant error: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
            return ResponseEntity.ok(new QueryResponse(msg));
        }
    }

    private static String compilePrompt(QueryRequest request) {
        StringBuilder sb = new StringBuilder();
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
