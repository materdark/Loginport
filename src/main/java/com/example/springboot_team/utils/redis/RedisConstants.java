package com.example.springboot_team.utils.redis;
//用来记录常用的常量
public class RedisConstants {
    public static final String LOGIN_CODE_KEY = "login:code:";
    public static final Long LOGIN_CODE_TTL = 2L;
    public static final String LOGIN_USER_KEY = "login:token:";
    public static final Long LOGIN_USER_TTL = 30L;
    public static final Long CACHE_NULL_TTL = 2L;
    public static final String LOCK_USER_KEY="lock:user:";
    public static  final  Long CACHE_USER_TTL=30L;
    public static final String CACHE_USER_KEY = "cache:user:";
    public static final String CACHE_PHONE_KEY="cache:phone:";
    public static final Long CACHE_PHONE_TTL=30L;

}
