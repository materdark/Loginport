package com.example.springboot_team.kafka.domain;

import com.example.springboot_team.dto.UserDto;
import lombok.Data;
/**
 * 生产者发送的特定信息*/
@Data
public class MessageMock {

    private Integer id ;
    private String name ;
    private UserDto userDto;
}