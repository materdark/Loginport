package com.example.springboot_team.service;

import com.aliyuncs.exceptions.ClientException;
import com.baomidou.mybatisplus.extension.service.IService;
import com.example.springboot_team.dto.QuitDto;
import com.example.springboot_team.dto.UserChangeDto;
import com.example.springboot_team.pojo.user_list;
import com.example.springboot_team.dto.Result;

import java.io.Serializable;

/**
* @author chenz
* @description 针对表【user_list】的数据库操作Service
* @createDate 2023-12-11 17:07:13
*/
public interface user_listService extends IService<user_list> {

    Result login(user_list userList);

//    Result getUserInfo(String token);

    Result checkUserName(String username);

    Result register(user_list userList);

    Result sendCode(String phone) throws ClientException;

    Result passwordChange(UserChangeDto userChangeDto);

    Result quit(QuitDto quitDto);
}
