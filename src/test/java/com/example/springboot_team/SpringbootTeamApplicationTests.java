package com.example.springboot_team;

import com.example.springboot_team.service.impl.user_listServiceImpl;
import com.example.springboot_team.utils.JwtHelper;
import jakarta.annotation.Resource;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class SpringbootTeamApplicationTests {
     @Resource
     private user_listServiceImpl userListService;
     @Test
    void testSaveUserRedis() {
        userListService.saveUserRedis("xjf",10L);
    }
    @Resource
    private JwtHelper jwtHelper;
     @Autowired
     private RocketMQTemplate rocketMQTemplate;
     @Test
    void testJwtHelper(){
         String username=jwtHelper.getUsername("");
         System.out.println(username);
     }

}
