package com.example.springboot_team.utils.kafka.Consumer;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.springboot_team.utils.kafka.domain.MessageMock;
import com.example.springboot_team.mapper.user_listMapper;
import com.example.springboot_team.pojo.user_list;
import com.example.springboot_team.utils.encryption.MD5Util;
import jakarta.annotation.Resource;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Transactional
@Service
public class KafkaPasswordChangeConsumer {
    @Resource
    private user_listMapper userListMapper;
    //数据库连接对象应该是重用对象，这样子可以提升代码的性能
    private LambdaQueryWrapper<user_list> lambdaQueryWrapper=new LambdaQueryWrapper<>();
    @KafkaListener(topics = "passwordChangeTopic", groupId = "my-group-id")
    public void consume(String message){
        //将获取的Json对象进行反序列化操作
        MessageMock messageMock= JSONUtil.toBean(message, MessageMock.class);
        //清除之前的查询条件，否则会拼接到之前的条件
        lambdaQueryWrapper.clear();
        //找到对应的应该更新的账号
        lambdaQueryWrapper.eq(user_list::getUsername,messageMock.getUserChangeDto().getUsername());
        user_list userPasswordChange = userListMapper.selectOne(lambdaQueryWrapper);
        //更新密码
        userPasswordChange.setPassword(MD5Util.encrypt(messageMock.getUserChangeDto().getNew_password()));
        userListMapper.update(userPasswordChange,lambdaQueryWrapper);

    }
}
