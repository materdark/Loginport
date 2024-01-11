package com.example.springboot_team.utils.Interceptor;

import com.example.springboot_team.dto.UserDto;
//用于存储本地线程
public class UserHolder {
    private static final ThreadLocal<UserDto> tl = new ThreadLocal<>();

    public static void saveUser(UserDto user){
        tl.set(user);
    }

    public static UserDto getUser(){
        return tl.get();
    }

    public static void removeUser(){
        tl.remove();
    }
}
