package com.example.travel.assistant.api;

import com.example.travel.assistant.service.AssistantService;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/assistant")
public class AssistantController {

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

    private final AssistantService assistantService;

    public AssistantController(AssistantService assistantService) {
        this.assistantService = assistantService;
    }

    @PostMapping("/ask")
    public ResponseEntity<AskResponse> ask(@RequestBody AskRequest request) {
        String reply = assistantService.ask(request.getPrompt());
        return ResponseEntity.ok(new AskResponse(reply));
    }
}