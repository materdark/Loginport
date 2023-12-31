package com.example.springboot_team.dto;

import lombok.Data;

@Data
//将手机注册前端所有数据放入当前对象中
public class LoginPhoneDto {
    private String phone;
    private String code;
}
