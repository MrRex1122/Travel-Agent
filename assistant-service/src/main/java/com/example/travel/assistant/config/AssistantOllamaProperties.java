package com.example.travel.assistant.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "assistant.ollama")
public class AssistantOllamaProperties {
    private String baseUrl = "http://localhost:11434";
    private String model = "llama3.1";
    private long requestTimeoutMs = 60000;
    private double temperature = 0.2;

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }

    public long getRequestTimeoutMs() { return requestTimeoutMs; }
    public void setRequestTimeoutMs(long requestTimeoutMs) { this.requestTimeoutMs = requestTimeoutMs; }

    public double getTemperature() { return temperature; }
    public void setTemperature(double temperature) { this.temperature = temperature; }
}