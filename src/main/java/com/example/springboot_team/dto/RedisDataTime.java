package com.example.springboot_team.dto;

import lombok.Data;

import java.time.LocalDateTime;

//逻辑过期时间数据类
@Data
public class RedisDataTime {
    private LocalDateTime expireTime;//过期时间
}
