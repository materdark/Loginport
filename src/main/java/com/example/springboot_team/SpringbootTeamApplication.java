package com.example.springboot_team;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@SpringBootApplication
public class SpringbootTeamApplication {

    public static void main(String[] args) {
        /*
         * 指定使用的日志框架，否则将会报错
         * RocketMQLog:WARN No appenders could be found for logger (io.netty.util.internal.InternalThreadLocalMap).
         * RocketMQLog:WARN Please initialize the logger system properly.
         */
        System.setProperty("rocketmq.client.logUseSlf4j", "true");
        SpringApplication.run(SpringbootTeamApplication.class, args
        );
    }

    @GetMapping("/")
    public String index(){
        return "test";
    }


}
