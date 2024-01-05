package com.example.springboot_team.controller;

import com.aliyuncs.exceptions.ClientException;
import com.example.springboot_team.dto.LoginPhoneDto;
import com.example.springboot_team.dto.QuitDto;
import com.example.springboot_team.dto.UserChangeDto;
import com.example.springboot_team.pojo.user_list;
import com.example.springboot_team.service.user_listService;
import com.example.springboot_team.dto.Result;
import com.example.springboot_team.service.user_phoneService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
@Slf4j
//数据以json的形式
@RestController
//实现跨域
@CrossOrigin
@RequestMapping("/frontapi/user")
//设置路径为user
public class UserController {


    @Resource
    private user_listService userListService;
    @Resource
    private user_phoneService userPhoneService;

    @PostMapping("login")
    public Result login(@RequestBody user_list userList){
        Result result = userListService.login(userList);
        return result;
    }
//    @GetMapping("getUserInfo")
//    public Result getUserInfo(@RequestHeader String token){
//        Result result=userListService.getUserInfo(token);
//        return  result;
//    }

    @PostMapping("register")
    public Result register(@RequestBody  user_list userList){
        Result result = userListService.register(userList);
        return result;
    }
    @PostMapping("code")
    public Result SendCode(@RequestParam("phone") String phone) throws ClientException {
                return userListService.sendCode(phone);
    }
    @PostMapping("phoneLogin")
    public Result phoneLogin(@RequestBody LoginPhoneDto loginPhoneDto){
        return userPhoneService.phoneLogin(loginPhoneDto);
    }
    @PostMapping("passwordChange")
    public Result passwordChange(@RequestBody UserChangeDto userChangeDto){
        return  userListService.passwordChange(userChangeDto);
    }
    @PostMapping ("quit")
    public Result quit(@RequestBody QuitDto quitDto)throws ClientException {
        Result result=userListService.quit(quitDto);
        return  result;
    }
}
