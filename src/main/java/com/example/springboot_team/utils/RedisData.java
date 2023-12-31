package com.example.springboot_team.utils;

import lombok.Data;

import java.time.LocalDateTime;
//逻辑过期时间工具类
@Data
public class RedisData {
    private LocalDateTime expireTime;//过期时间
    private Object data;
}
