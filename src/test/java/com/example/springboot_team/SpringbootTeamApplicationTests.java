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
    @Resource
    private JwtHelper jwtHelper;
     @Test
    void testJwtHelper(){
         String username=jwtHelper.getUsername("");
         System.out.println(username);
     }

}
