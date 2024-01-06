package com.example.springboot_team.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.springboot_team.dto.LoginPhoneDto;
import com.example.springboot_team.dto.UserDto;
import com.example.springboot_team.pojo.user_phone;
import com.example.springboot_team.service.user_phoneService;
import com.example.springboot_team.mapper.user_phoneMapper;
import com.example.springboot_team.dto.Result;
import com.example.springboot_team.utils.JwtHelper;
import com.example.springboot_team.utils.RedisData;
import com.example.springboot_team.utils.RegexUtils;
import com.example.springboot_team.utils.ResultCodeEnum;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.example.springboot_team.utils.Constants.*;

/**
 * @author chenz
 * @description 针对表【user_phone】的数据库操作Service实现
 * @createDate 2023-12-15 09:56:31
 */
@Transactional
@Service
@Slf4j
public class user_phoneServiceImpl extends ServiceImpl<user_phoneMapper, user_phone>
        implements user_phoneService{
    @Resource
    private JwtHelper jwtHelper;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private RedissonClient redissonClient;
    @Override
    public Result phoneLogin(LoginPhoneDto loginPhoneDto) {
        //先判断用户的手机号码格式是否正确
        String phone = loginPhoneDto.getPhone();
        if (RegexUtils.isPhoneInvalid(phone)) {
            // 2.如果不符合，返回错误信息
            return Result.build(null, ResultCodeEnum.PHONE_ERROR);
        }
        // 3.从redis获取验证码并校验
        String cacheCode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY + phone);
        String code = loginPhoneDto.getCode();
        //从redis中查询数据
        Boolean flag=RegisterPhoneUserRedis(loginPhoneDto,CACHE_PHONE_TTL);
        if (cacheCode == null || !cacheCode.equals(code)) {
            // 不一致，报错
            return Result.build(null,ResultCodeEnum.CODE_ERROR);
        }
        if(flag==true){
            //flag为true,说明用户不存在，此时redis中已经存入了用户信息，数据库中需要同步更新一下
            user_phone userPhone=new user_phone();
            userPhone = createUserWithPhone(phone);
        }
        // 生成token
        String token = jwtHelper.createToken(loginPhoneDto.getPhone());
        // 7.2.将User对象转为HashMap存储
        UserDto userDTO=new UserDto();
        userDTO.setPhoneNumber(phone);
        Map<String,String> userMap=new HashMap<>();
        userMap.put("phoneNumber",userDTO.getPhoneNumber());
        userMap.put("token",token);
        // 7.3.这里采用的是与用户登录相同的数据库，目的是保证拦截器读取的token都位于同样的位置，无需专门区分
        String tokenKey = LOGIN_USER_KEY+ userDTO.getPhoneNumber();
        stringRedisTemplate.opsForHash().putAll(tokenKey, userMap);
        // 7.4.设置token有效期
        stringRedisTemplate.expire(tokenKey, LOGIN_USER_TTL, TimeUnit.MINUTES);
        Map data = new HashMap();
        data.put("token",token);
        return Result.ok(data);

    }
    private user_phone createUserWithPhone(String phone) {
        // 1.创建用户
        user_phone userPhone = new user_phone();
        userPhone.setPhonenumber(phone);
        // 2.保存用户
        save(userPhone);
        return userPhone;
    }
    public Boolean RegisterPhoneUserRedis(LoginPhoneDto loginPhoneDto,Long expireSeconds){
        RLock lock = redissonClient.getLock("lock:order:" + loginPhoneDto.getPhone());
        boolean isLock = lock.tryLock();
        if(!isLock){
            // 获取锁失败，返回错误或重试
            log.error("已经有线程在使用了");
            return false;
        }
        try {
            String key = CACHE_PHONE_KEY + loginPhoneDto.getPhone();
            //从redis查询账号数据
            String Json = stringRedisTemplate.opsForValue().get(key);
            if (!(StrUtil.isBlank(Json))) {
                //3.如果存在，直接返回
                return false;
            }
            //封装逻辑过期
            RedisData redisData = new RedisData();
            redisData.setData(loginPhoneDto.getPhone());
            redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
            //写入Redis
            stringRedisTemplate.opsForValue().set(CACHE_PHONE_KEY + loginPhoneDto.getPhone(),
                    JSONUtil.toJsonStr(redisData));//这里设置的是自定义的过期时间
            return true;
        }
        finally {
            if (lock.isLocked() && lock.isHeldByCurrentThread()){
                // 释放锁
                lock.unlock();
            }
        }
    }
}




