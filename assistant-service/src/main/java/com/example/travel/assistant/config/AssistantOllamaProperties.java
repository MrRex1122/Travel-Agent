package com.example.travel.assistant.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "assistant.ollama")
public class AssistantOllamaProperties {
    private String baseUrl = "http://localhost:11434";
    private String model = "llama3.1";
    private String fallbackModel = ""; // optional; disabled by default
    private long requestTimeoutMs = 60000;
    private double temperature = 0.2;
    // Resource-tuning options to make large models run on low-end machines
    private int numGpu = 0; // force CPU by default (avoid CUDA_Host allocation)
    private int numCtx = 2048; // reduce context to save memory
    private boolean noFallback = true; // do not switch models unless explicitly enabled

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }

    public String getFallbackModel() { return fallbackModel; }
    public void setFallbackModel(String fallbackModel) { this.fallbackModel = fallbackModel; }

    public long getRequestTimeoutMs() { return requestTimeoutMs; }
    public void setRequestTimeoutMs(long requestTimeoutMs) { this.requestTimeoutMs = requestTimeoutMs; }

    public double getTemperature() { return temperature; }
    public void setTemperature(double temperature) { this.temperature = temperature; }

    public int getNumGpu() { return numGpu; }
    public void setNumGpu(int numGpu) { this.numGpu = numGpu; }

    public int getNumCtx() { return numCtx; }
    public void setNumCtx(int numCtx) { this.numCtx = numCtx; }

    public boolean isNoFallback() { return noFallback; }
    public void setNoFallback(boolean noFallback) { this.noFallback = noFallback; }
}