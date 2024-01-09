package com.example.springboot_team.controller;

import jakarta.annotation.Resource;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class KafkaController {

    @Resource
    private KafkaTemplate<String, String> kafkaTemplate;

    @PostMapping("/send")
    public void sendMessage(@RequestBody String message) {
        kafkaTemplate.send("my-topic", message);
    }

}


