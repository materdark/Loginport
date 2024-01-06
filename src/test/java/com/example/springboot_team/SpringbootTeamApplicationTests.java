package com.example.springboot_team;

import com.example.springboot_team.pojo.user_list;
import com.example.springboot_team.service.impl.user_listServiceImpl;
import com.example.springboot_team.utils.JwtHelper;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
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
     @Test
    void testJwtHelper(){
         String username=jwtHelper.getUsername("");
         System.out.println(username);
     }

}
