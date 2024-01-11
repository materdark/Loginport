package com.example.springboot_team.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.springboot_team.dto.*;
import com.example.springboot_team.kafka.domain.MessageMock;
import com.example.springboot_team.kafka.utils.KafkaSendResultHandler;
import com.example.springboot_team.pojo.user_phone;
import com.example.springboot_team.service.user_phoneService;
import com.example.springboot_team.mapper.user_phoneMapper;
import com.example.springboot_team.utils.JwtHelper;
import com.example.springboot_team.Regex.RegexUtils;
import com.example.springboot_team.utils.ResultCodeEnum;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.example.springboot_team.utils.Constants.*;
import static com.example.springboot_team.utils.ResultCodeEnum.THREAD_EXIST;

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
    private user_phoneMapper userPhoneMapper;
    @Resource
    private JwtHelper jwtHelper;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private RedissonClient redissonClient;
    @Resource
    private KafkaTemplate<Object, Object> kafkaTemplate;
    @Resource
    private KafkaSendResultHandler producerListener;
    private  static  final ExecutorService CACHE_REBUILD_EXECUTOR= Executors.newFixedThreadPool(10);
    private LambdaQueryWrapper<user_phone> lambdaQueryWrapper=new LambdaQueryWrapper<>();
    @Override
    public Result phoneLogin(LoginPhoneDto loginPhoneDto) {
        //先判断用户的手机号码格式是否正确
        String phone = loginPhoneDto.getPhone();
        if (RegexUtils.isPhoneInvalid(phone)) {
            // 2.如果不符合，返回错误信息
            return Result.build(null, ResultCodeEnum.PHONE_ERROR);
        }
        // 3.从redis获取验证码并校验
        //将查到的哈希数据取出来
        String codeKey=LOGIN_CODE_KEY + phone;
        Map<Object,Object> codeMap=stringRedisTemplate.opsForHash().entries(codeKey);
        CodeDto codeDto=BeanUtil.fillBeanWithMap(codeMap,new CodeDto(),false);
        //如果redis中没有，则报错返回
        if(codeDto==null){
            return Result.build(null,ResultCodeEnum.CODE_ERROR);
        }
        //从redis中获得key
        String cacheCode = codeDto.getCode();
        //前端发送的key
        String code = loginPhoneDto.getCode();
        //从redis中查询数据
       String flag=RegisterPhoneUserRedis(loginPhoneDto,CACHE_PHONE_TTL);
        if (!cacheCode.equals(code)) {
            // 验证码不一致，报错返回
            return Result.build(null,ResultCodeEnum.CODE_ERROR);
        }
        switch (flag){
            case "threadUsed":
               return Result.build(null,THREAD_EXIST);
            case "redisRebuild":
                //flag为redisRebuild,说明用户不存在，此时redis中已经存入了用户信息，数据库中需要异步更新一下
                //检验生产者发送的信息是否成功
                MessageMock messageMock=new MessageMock();
                user_phone userPhone=new user_phone();
                //将手机注册用户的手机号传入生产者发送信息中
                userPhone.setPhonenumber(loginPhoneDto.getPhone());
                messageMock.setUserPhone(userPhone);
                //将自定义的信息序列化
                String message=JSONUtil.toJsonStr(messageMock);
                //设置异步失败回调函数
                kafkaTemplate.setProducerListener(producerListener);
                kafkaTemplate.send("loginPhoneTopic",message);
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
        //只是单纯防止idea报错
        return Result.ok(null);
    }
    /**
     * 检验用户是否存在
     * 1.如果不存在，就将手机用户注册到数据库中
     * 2.如果存在，检验redis中的数据是否已经过期
     * 过期的话重新更新redis中的数据*/
    public String RegisterPhoneUserRedis(LoginPhoneDto loginPhoneDto,Long expireSeconds){
        RLock lock = redissonClient.getLock("lock:redis:" + loginPhoneDto.getPhone());
        boolean isLock = lock.tryLock();
        if(!isLock){
            // 获取锁失败，返回错误或重试
            log.error("已经有线程在使用了");
            return "threadUsed";
        }
        try {
            String RedisKey = CACHE_PHONE_KEY + loginPhoneDto.getPhone();
            //从redis中取出哈希结构的手机注册用户数据以及逻辑过期时间
            Map<Object,Object> userPhoneMap=stringRedisTemplate.opsForHash().entries(RedisKey);
            if (!(userPhoneMap.isEmpty())) {
                //3.如果存在手机用户数据，检验数据是否已经过期了
                //将查询到的哈希数据转为UserDto
                UserDto userDto= BeanUtil.fillBeanWithMap(userPhoneMap,new UserDto(),false);
                //需要将Json反序列化为对象
                RedisDataTime redisDataTime=JSONUtil.toBean(userDto.getRedisDataTime(), RedisDataTime.class);
                //获取逻辑过期时间
                LocalDateTime expireTime=redisDataTime.getExpireTime();
                if (expireTime.isAfter(LocalDateTime.now())) {
                    //5.1未过期，直接返回账号信息
                    return "redisRebuild";
                }
                this.saveUserPhoneRedis(userDto,userPhoneMap,expireSeconds);
                //5.2已过期，需要进行缓存重建
                return"redisRebuild";
            }
            //将新的手机用户写入数据库中
            Map<String,String> userRegisterMap=new HashMap<>();
            userRegisterMap.put("phoneNumber",loginPhoneDto.getPhone());
            //之所以又加入一个对象，是因为直接序列化逻辑过期时间会失败，原因不明
            RedisDataTime redisDataTime=new RedisDataTime();
            redisDataTime.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
            //插入写好的逻辑过期时间
            userRegisterMap.put("redisDataTime",JSONUtil.toJsonStr(redisDataTime));
            stringRedisTemplate.opsForHash().putAll(RedisKey,userRegisterMap);
            return "redisRebuild";
        }
        finally {
            if (lock.isLocked() && lock.isHeldByCurrentThread()){
                // 释放锁
                lock.unlock();
            }
        }
    }
    /**
     * 手机用户缓存重建函数
     *
     */
    public void saveUserPhoneRedis(UserDto userDto,Map<Object,Object> userPhoneMap,Long expireSeconds){
        RLock lock = redissonClient.getLock("lock:redis:" + userDto.getPhoneNumber());
        boolean isLock=lock.tryLock();
        if(isLock){
            //6.3成功，开启独立线程，实现缓存重建
            CACHE_REBUILD_EXECUTOR.submit(()->{
                try {
                    //清空之前的查询条件
                    lambdaQueryWrapper.clear();
                    //为了防止消费堆积，导致数据库重复插入数据，插入前必须先检验一下数据库中是否有账号，如果没有就插入数据库
                    lambdaQueryWrapper.eq(user_phone::getPhonenumber, userDto.getPhoneNumber());
                    user_phone userPhone = userPhoneMapper.selectOne(lambdaQueryWrapper);
                    userPhoneMap.put("phoneNumber", userDto.getPhoneNumber());
                    //之所以又加入一个对象，是因为直接序列化逻辑过期时间会失败，原因不明
                    RedisDataTime redisDataTime = new RedisDataTime();
                    redisDataTime.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
                    String RedisKey = CACHE_PHONE_KEY + userDto.getPhoneNumber();
                    //插入写好的逻辑过期时间
                    userPhoneMap.put("redisDataTime", JSONUtil.toJsonStr(redisDataTime));
                    stringRedisTemplate.opsForHash().putAll(RedisKey, userPhoneMap);
                }
                catch (Exception e){
                    throw new RuntimeException(e);
                }
                finally {
                    //判断是否释放线程锁的是否为获取线程锁的那个线程，防止其他线程过来解锁
                    if (lock.isLocked() && lock.isHeldByCurrentThread()) {
                        //释放锁
                        lock.unlock();
                    }
                }
            });
        }
    }
}




