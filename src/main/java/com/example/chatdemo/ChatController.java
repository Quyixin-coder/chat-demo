package com.example.chatdemo;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ChatController {

    private final DeepSeekService service;

    public ChatController(DeepSeekService service) {
        this.service = service;
    }

    @GetMapping("/chat")
    public String chat(@RequestParam String message) throws Exception {
        return service.chat(message);
    }
    @GetMapping("/chat2")
    public String chat2(@RequestParam String message) throws Exception {
        return service.chat(message);
    }
    @GetMapping("/assistant")
    public String assistant(@RequestParam String message) throws Exception {
        return service.assistant(message);
    }
    @GetMapping("/rag")
    public String rag(@RequestParam String message) throws Exception {
        return service.rag(message);
    }
}