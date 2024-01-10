package com.example.springboot_team.kafka.domain;

import com.example.springboot_team.dto.UserChangeDto;
import com.example.springboot_team.dto.UserDto;
import com.example.springboot_team.pojo.user_phone;
import lombok.Data;
/**
 * 生产者发送的特定信息*/
@Data
public class MessageMock {

    private Integer id ;
    private String name ;
    private UserDto userDto;
    private UserChangeDto userChangeDto;
    private user_phone userPhone;
}