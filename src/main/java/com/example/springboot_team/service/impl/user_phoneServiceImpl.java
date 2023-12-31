package com.example.springboot_team.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.springboot_team.dto.LoginPhoneDto;
import com.example.springboot_team.dto.UserDto;
import com.example.springboot_team.pojo.user_phone;
import com.example.springboot_team.service.user_phoneService;
import com.example.springboot_team.mapper.user_phoneMapper;
import com.example.springboot_team.dto.Result;
import com.example.springboot_team.utils.JwtHelper;
import com.example.springboot_team.utils.RegexUtils;
import com.example.springboot_team.utils.ResultCodeEnum;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    @Override
    public Result phoneLogin(LoginPhoneDto loginPhoneDto) {
        String phone = loginPhoneDto.getPhone();
        if (RegexUtils.isPhoneInvalid(phone)) {
            // 2.如果不符合，返回错误信息
            return Result.build(null, ResultCodeEnum.PHONE_ERROR);
        }
        // 3.从redis获取验证码并校验
        String cacheCode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY + phone);
        String code = loginPhoneDto.getCode();
        if (cacheCode == null || !cacheCode.equals(code)) {
            // 不一致，报错
            return Result.build(null,ResultCodeEnum.CODE_ERROR);
        }

        // 4.一致，根据手机号查询用户 select * from tb_user where phone = ?
        user_phone userPhone = query().eq("phonenumber", phone).one();

        // 5.判断用户是否存在
        if (userPhone == null) {
            // 6.不存在，创建新用户并保存
            userPhone = createUserWithPhone(phone);
        }

        // 7.保存用户信息到 redis中
        // 7.1.根据用户id生成token
        String token = jwtHelper.createToken(Long.valueOf(userPhone.getUid()));
        // 7.2.将User对象转为HashMap存储
          UserDto userDTO=new UserDto();
          userDTO.setId(userPhone.getUid());
          userDTO.setPhoneNumber(userPhone.getPhonenumber());
//          UserDto userDTO = BeanUtil.copyProperties(userPhone, UserDto.class);
//        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO, new HashMap<>(),
//                CopyOptions.create()
//                        .setIgnoreNullValue(true)
//                        .setFieldValueEditor((fieldName, fieldValue) -> fieldValue.toString()));
        Map<String,String> userMap=new HashMap<>();
        userMap.put("id",String.valueOf(userDTO.getId()));
        userMap.put("phoneNumber",userDTO.getPhoneNumber());
        // 7.3.存储
        String tokenKey = LOGIN_USER_KEY + token;
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
}




