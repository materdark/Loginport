package com.example.springboot_team;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@SpringBootApplication
public class SpringbootTeamApplication {

    public static void main(String[] args) {
        SpringApplication.run(SpringbootTeamApplication.class, args
        );
    }

    @GetMapping("/")
    public String index(){
        return "test";
    }


}
