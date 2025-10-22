package com.example.travel.assistant.api;

import com.example.travel.assistant.service.AgentService;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/assistant/agent")
public class AgentController {

    public static class AskRequest {
        private final String prompt;
        private final String sessionId;
        @JsonCreator
        public AskRequest(@JsonProperty("prompt") String prompt,
                          @JsonProperty("sessionId") String sessionId) {
            this.prompt = prompt;
            this.sessionId = sessionId;
        }
        public String getPrompt() { return prompt; }
        public String getSessionId() { return sessionId; }
    }

    public static class AskResponse {
        private final String answer;
        public AskResponse(String answer) { this.answer = answer; }
        public String getAnswer() { return answer; }
    }

    private final AgentService agentService;

    public AgentController(AgentService agentService) {
        this.agentService = agentService;
    }

    @PostMapping("/ask")
    public ResponseEntity<AskResponse> ask(@RequestBody(required = false) AskRequest request) {
        if (request == null) {
            return ResponseEntity.badRequest().body(new AskResponse("Bad request: empty body"));
        }
        if (request.getPrompt() == null || request.getPrompt().isBlank()) {
            return ResponseEntity.badRequest().body(new AskResponse("Please provide a non-empty 'prompt'."));
        }
        String memoryId = request.getSessionId() != null && !request.getSessionId().isBlank() ? request.getSessionId() : "default";
        String reply = agentService.ask(memoryId, request.getPrompt());
        return ResponseEntity.ok(new AskResponse(reply));
    }
}
