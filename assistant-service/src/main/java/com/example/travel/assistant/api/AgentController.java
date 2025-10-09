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
        @JsonCreator
        public AskRequest(@JsonProperty("prompt") String prompt) { this.prompt = prompt; }
        public String getPrompt() { return prompt; }
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
    public ResponseEntity<AskResponse> ask(@RequestBody AskRequest request) {
        String reply = agentService.ask(request.getPrompt());
        return ResponseEntity.ok(new AskResponse(reply));
    }
}
