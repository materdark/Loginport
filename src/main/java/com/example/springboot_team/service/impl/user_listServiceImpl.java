package com.example.springboot_team.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.alibaba.druid.util.StringUtils;
import com.aliyuncs.dysmsapi.model.v20170525.SendSmsResponse;
import com.aliyuncs.exceptions.ClientException;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.springboot_team.Regex.RegexUtils;
import com.example.springboot_team.dto.*;
import com.example.springboot_team.utils.encryption.JwtHelper;
import com.example.springboot_team.utils.encryption.MD5Util;
import com.example.springboot_team.utils.kafka.utils.KafkaSendResultHandler;
import com.example.springboot_team.utils.kafka.domain.MessageMock;
import com.example.springboot_team.mapper.user_listMapper;
import com.example.springboot_team.pojo.user_list;
import com.example.springboot_team.service.user_listService;
import com.example.springboot_team.utils.result.ResultCodeEnum;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
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

import static com.example.springboot_team.utils.RedisConstants.*;
import static com.example.springboot_team.utils.result.ResultCodeEnum.*;
import static com.example.springboot_team.utils.phone.SMSend.sendSms;

/**
* @author chenz
* @description 针对表【user_list】的数据库操作Service实现
* @createDate 2023-12-11 17:07:13
*/
@Transactional
@Slf4j
@Service
public class user_listServiceImpl extends ServiceImpl<user_listMapper, user_list>
    implements user_listService{
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private user_listMapper userListMapper;
    @Resource
    private JwtHelper jwtHelper;
    @Resource
    private RedissonClient redissonClient;
    @Resource
    private KafkaTemplate<Object, Object> kafkaTemplate;
    @Resource
    private KafkaSendResultHandler producerListener;
    private LambdaQueryWrapper<user_list> lambdaQueryWrapper=new LambdaQueryWrapper<>();

    /**
     * 登录业务
     *
     *   1.根据账号，通过redis查询用户对象  - loginUserRedis
     *   2.如果用户对象为null，查询失败，账号错误！ 501
     *   3.对比，密码 ，失败 返回503的错误
     *   4.根据用户id生成一个token, token -> result 返回
     */
    @Transactional
    @Override
    public Result login(@NotNull UserDto userDto)  {
        //直接从redis中获取信息，而非mysql中获取信息
        user_list loginUserRedis=queryWithLogicalExpire(userDto.getUsername());
        if(loginUserRedis==null){
            return Result.build(null,USERNAME_NULL);
        }
        userDto.setPassword(MD5Util.encrypt(userDto.getPassword()));
        //查看用户的密码是否存在且正确
        if (!StringUtils.isEmpty(userDto.getPassword())
                && userDto.getPassword().equals(loginUserRedis.getPassword())){
            //生成相应的token
            String token = jwtHelper.createToken(userDto.getUsername());
            //以哈希表的形式存入
            Map<String,String> usermap=new HashMap<>();
            usermap.put("username",userDto.getUsername());
            usermap.put("password",userDto.getPassword());
            usermap.put("token",token);
            String RedisKey = LOGIN_USER_KEY + userDto.getUsername();
            //存入token中
            stringRedisTemplate.opsForHash().putAll(RedisKey, usermap);
            // 7.4.设置token有效期
            stringRedisTemplate.expire(RedisKey, LOGIN_USER_TTL, TimeUnit.MINUTES);
            //将token封装到result返回
            Map data = new HashMap();
            data.put("token",token);
            return Result.ok(data);
        }
        return Result.build(null, PASSWORD_ERROR);
    }
    /**
     * 注册业务
     *  1.先在redis中插入数据
     *  2.然后在mysql中同步更新数据
     *  3.如果redis中用户已经存在，返回用户已经存在的结果
     *  4.返回结果
     * @param
     * @return
     */
    @Override
    @Transactional//保证数据库操作的原子性
    public Result register(@NotNull UserDto userDto) {
        //将接收到的数据进行浅拷贝,放入userDto中
        userDto.setPassword(MD5Util.encrypt(userDto.getPassword()));
        String flag=RegisterUserRedis(userDto,CACHE_USER_TTL);
        //使用switch而非多个if语句是因为分支的判断条件越多，switch 性能高的特性体现的就越明显。
        //这主要涉及到底层硬件
        switch(flag){
            case "redisUserExist":
                return Result.build(null,USERNAME_USED);
            case "threadUsed":
                return Result.build(null,THREAD_EXIST);
            case "redisRebuild":
                //redis更新完后将信息通过生产者传给消费者，消费者负责执行剩下的部分
                //检验生产者发送的信息是否成功
                MessageMock messageMock=new MessageMock();
                messageMock.setUserDto(userDto);
                //将自定义的信息序列化
                String message=JSONUtil.toJsonStr(messageMock);
                //设置异步失败回调函数
                kafkaTemplate.setProducerListener(producerListener);
                kafkaTemplate.send("registerTopic",message);
                return Result.ok(null);
        }
        //不加的话会报错
        return Result.ok(null);
    }
    /**
     * 发送验证码业务
     *  1.检查手机格式是否符合规范
     *  2.不符合返回错误码
     *  3.符合，生成六位验证码
     *  4.返回结果
     * @param phone
     * @return
     */

    @Override
    public Result sendCode(String phone) throws ClientException {
        // 1.校验手机号
        if (RegexUtils.isPhoneInvalid(phone)) {
            // 2.如果不符合，返回错误信息
            return Result.build(null, ResultCodeEnum.PHONE_ERROR);
        }
        // 3.符合，生成验证码
        String code = RandomUtil.randomNumbers(6);
        //连接阿里云远程平台发送短信
        SendSmsResponse sendSms = sendSms(phone,code);
        // 4.保存验证码到 redis中，以哈希结构的形式
        Map<String,String> codeMap=new HashMap<>();
        codeMap.put("code",code);
        String codeKey=LOGIN_CODE_KEY + phone;
        stringRedisTemplate.opsForHash().putAll(codeKey,codeMap);
        stringRedisTemplate.expire(codeKey,LOGIN_CODE_TTL,TimeUnit.MINUTES);
        // 返回ok
        return Result.ok(null);
    }
    /**
     * 修改密码服务
     *  1.检查账号是否存在
     *  2.检查账号与密码是否正确
     *  3.正确则修改账号的新密码
     *  4.返回结果
     * @return
     */

    @Override
    public Result passwordChange(UserChangeDto userChangeDto)  {
        String flag=passwordChangeUserRedis(userChangeDto,CACHE_USER_TTL);
        switch (flag){
            case "threadUsed":
                return Result.build(null,THREAD_EXIST);
            case "passwordError":
                return Result.build(null,PASSWORD_ERROR);
                //锁的异常可以加也可以不加，无关紧要
            case "lockError":
                return Result.build(null,LOCK_ERRO);
            case "redisRebuild":
                //检验生产者发送的信息是否成功
                MessageMock messageMock=new MessageMock();
                messageMock.setUserChangeDto(userChangeDto);
                //将自定义的信息序列化
                String message=JSONUtil.toJsonStr(messageMock);
                //设置异步失败回调函数
                kafkaTemplate.setProducerListener(producerListener);
                kafkaTemplate.send("passwordChangeTopic",message);
                return  Result.ok(null);
        }
        //主要是防止报错
        return Result.ok(null);
    }
    /**
     * 退出登录服务
     * 1.从本地线程中获取前端请求的token
     * 2.根据前端的token删除redis中的token
     * 3.返回相应的结果
     * */

    @Override
    public Result quit(@NotNull QuitDto quitDto) {
        String tokenKey=LOGIN_USER_KEY+quitDto.getToken();
        stringRedisTemplate.delete(tokenKey);
        return Result.ok(null);
    }

    private  static  final ExecutorService CACHE_REBUILD_EXECUTOR= Executors.newFixedThreadPool(10);
    /**查询用户缓存的逻辑过期
     * 1.如果用户未存在缓存中，直接返回null
     * 2.如果过期则进行缓存重建
     * 3.返回缓存结果
     * */

    public user_list queryWithLogicalExpire(String username)  {
        String key=CACHE_USER_KEY+username;
        //从redis中取出哈希结构的用户数据以及逻辑过期时间
        Map<Object,Object> userMap=stringRedisTemplate.opsForHash().entries(key);
        //2.判断是否存在于redis中
        if(userMap.isEmpty()){
            LambdaQueryWrapper<user_list> lambdaQueryWrapper=new LambdaQueryWrapper<>();
            //这里没有使用两个lambdaQueryWrapper而是直接用同一个，是因为mysql的连接很耗费性能，共用一个连接比较节省性能
            lambdaQueryWrapper.eq(user_list::getUsername,username);
            user_list userList = userListMapper.selectOne(lambdaQueryWrapper);
            //3.判断是否存在于mysql中。这里使用两层if嵌套，
            // 纠结了很久,最后认为采用两层的性能在一般情况下，性能比复合条件语句高，因为触发两层循环的条件概率比较小
            //复合条件使用的话无论如何都比触发第一层条件消耗的性能大
            //如果mysql中也没有的话，直接返回null
            if(userList==null){
                return null;
            }
            //如果mysql中有的话，进行将mysql中的数据库重新写入redis中的操作
            else {
                this.saveUserRedis(userList,CACHE_USER_TTL);
                //将数据存储到redis中后，将查到的mysql数据返回
                return userList;
            }
        }
        //将查询到的hash数据转为UserDto
        UserDto userDto= BeanUtil.fillBeanWithMap(userMap,new UserDto(),false);
        //4.命中，需要先把json反序列化为对象
        RedisDataTime redisDataTime=JSONUtil.toBean(userDto.getRedisDataTime(), RedisDataTime.class);
        //获取逻辑过期时间
        LocalDateTime expireTime = redisDataTime.getExpireTime();
        user_list userList=new user_list();
        userList.setPassword(userDto.getPassword());
        userList.setUsername(userDto.getUsername());
        //5.判读是否过期
        if (expireTime.isAfter(LocalDateTime.now())) {
          //5.1未过期，直接返回账号信息
            return userList;
        }
        //5.2已过期，需要进行缓存重建
         this.saveUserRedis(userList,20L);
        //6.4获取锁失败的用户返回过期的用户信息，获取锁成功的用户返回更新后的用户信息
         return userList;
    }
    /**
     * 写入redis缓存服务
     *  1.查询redis
     *  2.封装缓存的逻辑过期
     *  3.写入redis缓存中
     * @return
     */
        public void saveUserRedis(@NotNull user_list userList, Long expireSeconds) {
            //获取互斥锁,进行缓存重建
            RLock lock = redissonClient.getLock("lock:redis:" + userList.getUsername());
            boolean isLock = lock.tryLock();
            if (isLock) {
                //6.3成功，开启独立线程，实现缓存重建
                CACHE_REBUILD_EXECUTOR.submit(()->{
                    try {
                        //清空之前的查询条件
                        lambdaQueryWrapper.clear();
                        lambdaQueryWrapper.eq(user_list::getUsername,userList.getUsername());
                        //从数据库中查询数据，将其同步更新到redis中
                        user_list redisRebuild = userListMapper.selectOne(lambdaQueryWrapper);
                        //将数据以哈希结构的形式存入,进行逻辑过期封装
                        Map<String,String> usermap=new HashMap<>();
                        usermap.put("username",redisRebuild.getUsername());
                        usermap.put("password",redisRebuild.getPassword());
                        //之所以又加一个对象接收逻辑过期是因为，直接序列化时间类会序列化失败，原因不清楚
                        RedisDataTime redisDataTime=new RedisDataTime();
                        redisDataTime.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
                        //插入定义好的逻辑过期时间
                        usermap.put("redisDataTime",JSONUtil.toJsonStr(redisDataTime));
                        String RedisKey=CACHE_USER_KEY + redisRebuild.getUsername();
                        stringRedisTemplate.opsForHash().putAll(RedisKey, usermap);
                    }
                    catch (Exception e) {
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
          public String RegisterUserRedis(@NotNull UserDto loginUser, Long expireSeconds){
              RLock lock = redissonClient.getLock("lock:redis:" + loginUser.getUsername());
              boolean isLock = lock.tryLock();
              if(!isLock){
                  // 获取锁失败，返回错误或重试
                  log.error("已经有线程在使用了");
                  return "threadUsed";
              }
              try {
                  String key = CACHE_USER_KEY + loginUser.getUsername();
                  //从redis查询账号数据
                  String Json = stringRedisTemplate.opsForValue().get(key);
                  if (!(StrUtil.isBlank(Json))) {
                      //3.如果存在，直接返回
                      return "redisUserExist";
                  }
                  //将数据以哈希结构的形式存入,进行逻辑过期封装
                  Map<String,String> usermap=new HashMap<>();
                  usermap.put("username",loginUser.getUsername());
                  usermap.put("password",loginUser.getPassword());
                  //之所以又加一个对象接收逻辑过期是因为，直接序列化时间类会序列化失败，原因不清楚
                  RedisDataTime redisDataTime=new RedisDataTime();
                  redisDataTime.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
                  //插入定义好的逻辑过期时间
                  usermap.put("redisDataTime",JSONUtil.toJsonStr(redisDataTime));
                  String RedisKey=CACHE_USER_KEY + loginUser.getUsername();
                  //写入redis中
                  stringRedisTemplate.opsForHash().putAll(RedisKey, usermap);
                  return "redisRebuild";
              }
              finally {
                  if (lock.isLocked() && lock.isHeldByCurrentThread()) {
                      // 释放锁
                      lock.unlock();
                  }
              }
          }

    public String passwordChangeUserRedis(@NotNull UserChangeDto userChangeDto, Long expireSeconds) {
        RLock lock = redissonClient.getLock("lock:redis:" + userChangeDto.getUsername());
        boolean isLock = lock.tryLock();
        if(!isLock){
            // 获取锁失败，返回错误或重试
            log.error("已经有线程在使用了");
            return "threadUsed";
        }
        try {
            //从redis中获取信息
            user_list passwordChangeUserRedis=queryWithLogicalExpire(userChangeDto.getUsername());
            //检验旧的账号密码是否一致
            if (!StringUtils.isEmpty(userChangeDto.getOld_password())
                    && MD5Util.encrypt(userChangeDto.getOld_password()).equals(passwordChangeUserRedis.getPassword())){
                //密码一致，则将新的密码插入redis中
                UserDto loginUser=new UserDto();
                loginUser.setUsername(userChangeDto.getUsername());
                loginUser.setPassword(MD5Util.encrypt(userChangeDto.getNew_password()));
                //将数据以哈希结构的形式存入,进行逻辑过期封装
                Map<String,String> usermap=new HashMap<>();
                usermap.put("username",loginUser.getUsername());
                usermap.put("password",loginUser.getPassword());
                //之所以又加一个对象接收逻辑过期是因为，直接序列化时间类会序列化失败，原因不清楚
                RedisDataTime redisDataTime=new RedisDataTime();
                redisDataTime.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
                //插入定义好的逻辑过期时间
                usermap.put("redisDataTime",JSONUtil.toJsonStr(redisDataTime));
                String RedisKey=CACHE_USER_KEY + loginUser.getUsername();
                //写入redis中
                stringRedisTemplate.opsForHash().putAll(RedisKey, usermap);
                return "redisRebuild";
            }
            return "passwordError";
            }
        catch (IllegalMonitorStateException e){
            log.error("出现锁异常");
            return "lockError";
        }
        finally {
            if (lock.isLocked() && lock.isHeldByCurrentThread()){
                // 释放锁
                lock.unlock();
            }
        }
    }
}




