package com.example.springboot_team.service;

import com.example.springboot_team.dto.LoginPhoneDto;
import com.example.springboot_team.dto.Result;
import com.example.springboot_team.pojo.user_phone;
import com.baomidou.mybatisplus.extension.service.IService;

/**
* @author chenz
* @description 针对表【user_phone】的数据库操作Service
* @createDate 2023-12-15 17:29:51
*/
public interface user_phoneService extends IService<user_phone> {

    Result phoneLogin(LoginPhoneDto loginPhoneDto);
}
