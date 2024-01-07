package com.example.springboot_team.service.impl;

import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.alibaba.druid.util.StringUtils;
import com.aliyuncs.dysmsapi.model.v20170525.SendSmsResponse;
import com.aliyuncs.exceptions.ClientException;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.springboot_team.Regex.RegexUtils;
import com.example.springboot_team.dto.QuitDto;
import com.example.springboot_team.dto.Result;
import com.example.springboot_team.dto.UserChangeDto;
import com.example.springboot_team.dto.UserDto;
import com.example.springboot_team.mapper.user_listMapper;
import com.example.springboot_team.pojo.user_list;
import com.example.springboot_team.service.user_listService;
import com.example.springboot_team.utils.*;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.apache.rocketmq.spring.support.RocketMQHeaders;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.example.springboot_team.utils.Constants.*;
import static com.example.springboot_team.utils.ResultCodeEnum.PASSWORD_ERROR;
import static com.example.springboot_team.utils.ResultCodeEnum.USERNAME_USED;
import static com.example.springboot_team.utils.SMSend.sendSms;

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
    private RocketMQTemplate rocketMQTemplate;

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
    public Result login(UserDto userDto) {
        //直接从redis中获取信息，而非mysql中获取信息
        user_list loginUserRedis=queryWithLogicalExpire(userDto.getUsername());
        //查看用户的密码是否存在且正确
        if (!StringUtils.isEmpty(userDto.getPassword())
                && MD5Util.encrypt(userDto.getPassword()).equals(loginUserRedis.getPassword())){
            //生成相应的token
            String token = jwtHelper.createToken(userDto.getUsername());
            //以哈希表的形式存入
            Map<String,String> usermap=new HashMap<>();
            usermap.put("username",userDto.getUsername());
            usermap.put("password",userDto.getPassword());
            usermap.put("token",token);
            String tokenKey = LOGIN_USER_KEY + userDto.getUsername();
            //存入token中
            stringRedisTemplate.opsForHash().putAll(tokenKey, usermap);
            // 7.4.设置token有效期
            stringRedisTemplate.expire(tokenKey, LOGIN_USER_TTL, TimeUnit.MINUTES);
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
    public Result register(UserDto userDto) {
        //将接收到的数据进行浅拷贝,放入userDto中

        userDto.setPassword(MD5Util.encrypt(userDto.getPassword()));
        Boolean flag=RegisterUserRedis(userDto,CACHE_USER_TTL);
        if(flag==false){
            return Result.build(null,USERNAME_USED);
        }
        //redis更新完后将信息通过生产者传给消费者，消费者负责执行剩下的部分
        RegisterSendMsg(userDto);
        user_list userList=new user_list();
        userList.setPassword(userDto.getPassword());
        userList.setUsername(userDto.getUsername());
        //数据库中插入数据
        userListMapper.insert(userList);
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
            return Result.build(null,ResultCodeEnum.PHONE_ERROR);
        }
        // 3.符合，生成验证码
        String code = RandomUtil.randomNumbers(6);
        //连接阿里云远程平台发送短信
        SendSmsResponse sendSms = sendSms(phone,code);
        // 4.保存验证码到 redis中
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY + phone, code, LOGIN_CODE_TTL, TimeUnit.MINUTES);

        // 5.发送验证码
        log.debug("发送短信验证码成功，验证码：{}");
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
    public Result passwordChange(UserChangeDto userChangeDto) {
        Boolean flag=passwordChangeUserRedis(userChangeDto,CACHE_USER_TTL);
        if(flag==false){
            return Result.build(null,PASSWORD_ERROR);
        }
        else {
            LambdaQueryWrapper<user_list> lambdaQueryWrapper = new LambdaQueryWrapper<>();
            lambdaQueryWrapper.eq(user_list::getUsername,userChangeDto.getUsername());
            user_list userPasswordChange = userListMapper.selectOne(lambdaQueryWrapper);
            //更新密码
            userPasswordChange.setPassword(MD5Util.encrypt(userChangeDto.getNew_password()));
            userListMapper.update(userPasswordChange,lambdaQueryWrapper);
            return  Result.ok(null);
        }
    }
    /**
     * 退出登录服务
     * 1.从本地线程中获取前端请求的token
     * 2.根据前端的token删除redis中的token
     * 3.返回相应的结果
     * */

    @Override
    public Result quit(QuitDto quitDto) {
        String tokenKey=LOGIN_USER_KEY+quitDto.getToken();
        stringRedisTemplate.delete(tokenKey);
        return Result.ok(null);
    }

    /**获取锁函数
     * 1.获取相应的锁
     * 2.装箱并返回结果
     * */


//    private boolean tryLock(String key){
//        Boolean flag=stringRedisTemplate.opsForValue().setIfAbsent(key,"1",0,TimeUnit.SECONDS);
//        return BooleanUtil.isTrue(flag);//装箱操作，因为flag会被拆箱，flag不应该是null
//    }
//
//    /**释放锁函数
//     * redis中直接删除即可
//     * */
//
//    //释放锁
//    private void  unlock(String key){
//        stringRedisTemplate.delete(key);
//    }
//    /**生成一个线程池,容纳100个线程
//     * */

    private  static  final ExecutorService CACHE_REBUILD_EXECUTOR= Executors.newFixedThreadPool(100);
    /**查询用户缓存的逻辑过期
     * 1.如果用户未存在缓存中，直接返回null
     * 2.如果过期则进行缓存重建
     * 3.返回缓存结果
     * */

    public user_list queryWithLogicalExpire(String username){
        String key=CACHE_USER_KEY+username;
        //从redis查询账号缓存
        String Json=stringRedisTemplate.opsForValue().get(key);
        //2.判断是否存在
        if(StrUtil.isBlank(Json)){
            //3.不存在，直接返回
            return null;
        }
        //4.命中，需要先把json反序列化为对象
        RedisData redisData = JSONUtil.toBean(Json, RedisData.class);
        user_list userList = JSONUtil.toBean((JSONObject) redisData.getData(), user_list.class);
        LocalDateTime expireTime = redisData.getExpireTime();
        //5.判读是否过期
        if (expireTime.isAfter(LocalDateTime.now())) {
          //5.1未过期，直接返回账号信息
            return userList;
        }
        //5.2已过期，需要进行缓存重建
        //6缓存重建
        //6.1获取互斥锁
        RLock lock = redissonClient.getLock("lock:order:" + username);
        boolean isLock = lock.tryLock();
        //6.2判断是否获取锁成功
        if (isLock) {
            //6.3成功，开启独立线程，实现缓存重建
            CACHE_REBUILD_EXECUTOR.submit(()->{
                try {
                    this.saveUserRedis(username,20L);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    if (lock.isLocked() && lock.isHeldByCurrentThread()) {
                        //释放锁
                        lock.unlock();
                    }
                }
            });

        }
        //6.4返回过期的用户信息
         return userList;
    }
    /**
     * 写入redis缓存服务
     *  1.查询redis
     *  2.封装缓存的逻辑过期
     *  3.写入redis缓存中
     * @return
     */
        public void saveUserRedis(String username,Long expireSeconds){
            LambdaQueryWrapper<user_list> lambdaQueryWrapper = new LambdaQueryWrapper<>();
           //查询账号
         lambdaQueryWrapper.eq(user_list::getUsername,username);
         user_list loginUser = userListMapper.selectOne(lambdaQueryWrapper);
          //封装逻辑过期
          RedisData redisData=new RedisData();
          redisData.setData(loginUser);
          redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
          //写入Redis
          stringRedisTemplate.opsForValue().set(CACHE_USER_KEY+loginUser.getUsername(),
                JSONUtil.toJsonStr(redisData));//这里设置的是自定义的过期时间
    }
          public Boolean RegisterUserRedis(UserDto loginUser,Long expireSeconds){
              RLock lock = redissonClient.getLock("lock:order:" + loginUser.getUsername());
              boolean isLock = lock.tryLock();
              if(!isLock){
                  // 获取锁失败，返回错误或重试
                  log.error("已经有线程在使用了");
                  return false;
              }
              try {
                  String key = CACHE_USER_KEY + loginUser.getUsername();
                  //从redis查询账号数据
                  String Json = stringRedisTemplate.opsForValue().get(key);
                  if (!(StrUtil.isBlank(Json))) {
                      //3.如果存在，直接返回
                      return false;
                  }
                  //封装逻辑过期
                  RedisData redisData = new RedisData();
                  redisData.setData(loginUser);
                  redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
                  //写入Redis
                  stringRedisTemplate.opsForValue().set(CACHE_USER_KEY + loginUser.getUsername(),
                          JSONUtil.toJsonStr(redisData));//这里设置的是自定义的过期时间
                  return true;
              }
              finally {
                  if (lock.isLocked() && lock.isHeldByCurrentThread()) {
                      // 释放锁
                      lock.unlock();
                  }
              }
          }

    public void RegisterSendMsg(UserDto userDto){
        rocketMQTemplate.asyncSend("bootTestTopic", userDto.toString(), new SendCallback() {
            @Override
            public void onSuccess(SendResult sendResult) {

            }

            @Override
            public void onException(Throwable throwable) {

            }
        });
    }
    public Boolean passwordChangeUserRedis(UserChangeDto userChangeDto,Long expireSeconds){
        RLock lock = redissonClient.getLock("lock:order:" + userChangeDto.getUsername());
        boolean isLock = lock.tryLock();
        if(!isLock){
            // 获取锁失败，返回错误或重试
            log.error("已经有线程在使用了");
            return false;
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
                //封装逻辑过期
                RedisData redisData = new RedisData();
                redisData.setData(loginUser);
                redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
                //写入Redis
                stringRedisTemplate.opsForValue().set(CACHE_USER_KEY + loginUser.getUsername(),
                        JSONUtil.toJsonStr(redisData));//这里设置的是自定义的过期时间
                return true;
            }
            return false;
            }
        catch (IllegalMonitorStateException e){
            log.error("出现锁异常");
            return false;
        }
        finally {
            if (lock.isLocked() && lock.isHeldByCurrentThread()){
                // 释放锁
                lock.unlock();
            }
        }
    }
}




