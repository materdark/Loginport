package com.example.springboot_team.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.alibaba.druid.util.StringUtils;
import com.aliyuncs.dysmsapi.model.v20170525.SendSmsResponse;
import com.aliyuncs.exceptions.ClientException;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
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
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.example.springboot_team.utils.Constants.*;
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
    private int userTimeCount=0;
    Date lastTime=new Date();
    /**
     * 登录业务
     *
     *   1.根据账号，查询用户对象  - loginUser
     *   2.如果用户对象为null，查询失败，账号错误！ 501
     *   3.对比，密码 ，失败 返回503的错误
     *   4.根据用户id生成一个token, token -> result 返回
     */
    @Override
    public Result login(user_list userList) {
        //当用户登录次数超过5个时说明为热点数据，此时从redis获取用户的相关数据
        if(userTimeCount>=5){
            user_list loginUserRedis=queryWithLogicalExpire(userList.getUsername());
            String token = jwtHelper.createToken(Long.valueOf(loginUserRedis.getUid()));
            Map data = new HashMap();
            data.put("token",token);
            data.put("userTimeCount",userTimeCount);
            data.put("redis","Yes");
            return Result.ok(data);
        }
        LambdaQueryWrapper<user_list> lambdaQueryWrapper_login = new LambdaQueryWrapper<>();
        lambdaQueryWrapper_login.eq(user_list::getUsername,userList.getUsername());
        //先查询redis缓存中是否有账号
        //根据账号查询数据
        user_list loginUser = userListMapper.selectOne(lambdaQueryWrapper_login);
        if (loginUser == null) {
            return Result.build(null, ResultCodeEnum.USERNAME_ERROR);
        }

        //对比密码
        if (!StringUtils.isEmpty(userList.getPassword())
                && MD5Util.encrypt(userList.getPassword()).equals(loginUser.getPassword())){
            //获取当前的时间
            Date currentTime=new Date();
            //判断当前时间与上个次登录的时间是否在一个月以内
            userTimeCount = isMoth(currentTime, lastTime);
            lastTime=currentTime;
            //如果一个月内用户登录的次数大于等于5次，则将他作为热点数据进行保存
            if(userTimeCount==5){
                saveUserRedis(userList.getUsername(),CACHE_USER_TTL);
            }
            //登录成功
            //根据用户id生成 token
            //生成用户登录的token令牌
            String token = jwtHelper.createToken(Long.valueOf(loginUser.getUid()));
            UserDto userDto=new UserDto();
            userDto.setUsername(userList.getUsername());
            userDto.setPassword(userList.getPassword());
            Map<String,String> usermap=new HashMap<>();
            usermap.put("username",userDto.getUsername());
            usermap.put("password",userDto.getPassword());
            String tokenKey = LOGIN_USER_KEY + token;
            //存入token中
            stringRedisTemplate.opsForHash().putAll(tokenKey, usermap);
            // 7.4.设置token有效期
            stringRedisTemplate.expire(tokenKey, LOGIN_USER_TTL, TimeUnit.MINUTES);
            //将token封装到result返回
            Map data = new HashMap();
            data.put("token",token);
            data.put("userTimeCount",userTimeCount);
            return Result.ok(data);
        }

        //密码错误
        return Result.build(null,ResultCodeEnum.PASSWORD_ERROR);
    }
    /**
     * 根据token获取用户数据
     *
     *  1. token是否在有效期
     *  2. 根据token解析userId
     *  3. 根据用户id查询用数据
     *  4. 去掉密码，封装result结果返回即可
     *
     * @param token
     * @return
     */
//    @Override
//    public Result getUserInfo(String token) {
//        //是否过期 true过期
//        boolean expiration = jwtHelper.isExpiration(token);
//
//        if (expiration) {
//            //失效，未登录看待
//            return Result.build(null,ResultCodeEnum.NOTLOGIN);
//        }
//
//        int userId = jwtHelper.getUserId(token).intValue();
//        user_list user = userListMapper.selectById(userId);
////        user.setPassword("");
//
//        Map data = new HashMap();
//        data.put("loginUser",user);
//
//        return Result.ok(data);
//    }

    /**
     * 检查账号是否可用
     *   1.根据账号进行count查询
     *   2.count == 0 可用
     *   3.count > 0 不可用
     * @param username 账号
     * @return
     */
    @Override
    public Result checkUserName(String username) {
        LambdaQueryWrapper<user_list> lambdaQueryWrapper = new LambdaQueryWrapper<>();
        lambdaQueryWrapper.eq(user_list::getUsername,username);
        Long count = userListMapper.selectCount(lambdaQueryWrapper);

        if (count == 0) {
            return Result.ok(null);
        }
        return Result.build(null,ResultCodeEnum.USERNAME_USED);
    }
    /**
     * 注册业务
     *  1.依然检查账号是否已经被注册
     *  2.密码加密处理
     *  3.账号数据保存
     *  4.返回结果
     * @param userList
     * @return
     */
    @Override
    public Result register(user_list userList) {

        LambdaQueryWrapper<user_list> queryWrapper
                = new LambdaQueryWrapper<>();
        queryWrapper.eq(user_list::getUsername,userList.getUsername());
        Long count = userListMapper.selectCount(queryWrapper);
        if (count > 0) {
            return Result.build(null,ResultCodeEnum.USERNAME_USED);
        }

        userList.setPassword(MD5Util.encrypt(userList.getPassword()));

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
        LambdaQueryWrapper<user_list> lambdaQueryWrapper = new LambdaQueryWrapper<>();
        lambdaQueryWrapper.eq(user_list::getUsername,userChangeDto.getUsername());
        user_list userPasswordChange = userListMapper.selectOne(lambdaQueryWrapper);
        if (userPasswordChange == null) {
            return Result.build(null, ResultCodeEnum.USERNAME_ERROR);
        }
        if (!StringUtils.isEmpty(userChangeDto.getOld_password())
                &&MD5Util.encrypt(userChangeDto.getOld_password()).equals(userPasswordChange.getPassword())){
            //更新密码
            userPasswordChange.setPassword(MD5Util.encrypt(userChangeDto.getNew_password()));
            userListMapper.update(userPasswordChange,lambdaQueryWrapper);
        }
        return  Result.ok(null);
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


    private boolean tryLock(String key){
        Boolean flag=stringRedisTemplate.opsForValue().setIfAbsent(key,"1",0,TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);//装箱操作，因为flag会被拆箱，flag不应该是null
    }

    /**释放锁函数
     * redis中直接删除即可
     * */

    //释放锁
    private void  unlock(String key){
        stringRedisTemplate.delete(key);
    }
    /**生成一个线程池,容纳100个线程
     * */

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
        String lockKey=LOCK_USER_KEY+username;
        boolean isLock = tryLock(lockKey);
        //6.2判断是否获取锁成功
        if (isLock) {
            //6.3成功，开启独立线程，实现缓存重建
            CACHE_REBUILD_EXECUTOR.submit(()->{
                try {
                    this.saveUserRedis(username,20L);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    //释放锁
                    unlock(lockKey);
                }
            });

        }
        //6.4返回过期的商铺信息
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
    /**判断当前登录时间与上次登录时间是否在同个月
     * 如果是同个月则当前次数加一，不是则清零为1
     * */
    public int isMoth(Date currentTime,Date lastTime){
        Calendar calendar1 = Calendar.getInstance();
        Calendar calendar2=Calendar.getInstance();
        calendar1.setTime(currentTime);
        calendar2.setTime(lastTime);
        int year1 =calendar1.get(Calendar.YEAR);
        int year2 =calendar2.get(Calendar.YEAR);
        int month1 =calendar1.get(Calendar.MONTH);
        int month2 =calendar2.get(Calendar.MONTH);
        if(year1==year2 && month1== month2){
            userTimeCount+=1;
        }
        else {
            userTimeCount=1;
        }
        return  userTimeCount;
    }
}




