package com.example.travel.assistant.service;

import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;


@SpringBootTest
class AssistantServiceTest {

    @Resource
    AssistantService assistantService;

    @Test
    void ask() {
        String hello = assistantService.ask("hello");
        System.out.println(hello);
    }
}