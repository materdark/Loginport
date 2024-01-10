package com.example.springboot_team.kafka.Consumer;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.springboot_team.kafka.domain.MessageMock;
import com.example.springboot_team.mapper.user_listMapper;
import com.example.springboot_team.pojo.user_list;
import jakarta.annotation.Resource;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


/**
 * 消费者负责监听生产者发送过来的数据
 * */
@Transactional
@Service
public class KafkaRegisterConsumer {
    @Resource
    private user_listMapper userListMapper;

    private LambdaQueryWrapper<user_list> lambdaQueryWrapper=new LambdaQueryWrapper<>();
    //线程锁池，提高并发效率
    @KafkaListener(topics = "registerTopic", groupId = "my-group-id")
    public void consume(String message) {
        //将获取的Json对象进行反序列化操作
        MessageMock messageMock=JSONUtil.toBean(message, MessageMock.class);
        //清除之前的拼接条件
        lambdaQueryWrapper.clear();
        //为了防止消费堆积，导致数据库重复插入数据，插入前必须先检验一下数据库中是否有账号，如果没有就插入数据库
        lambdaQueryWrapper.eq(user_list::getUsername,messageMock.getUserDto().getUsername());
        user_list userList = userListMapper.selectOne(lambdaQueryWrapper);
        if(userList==null){
            //之所以要新建一个，而不是用之前的userList
            // 是因为我发现除了初始化的对象以外，其他对象比如上个userList==null时，空对象使用方法会自动结束
            //这触发了java底层垃圾回收机制，这属于空对象模式
            user_list userList1=new user_list();
            userList1.setUsername(messageMock.getUserDto().getUsername());
            userList1.setPassword(messageMock.getUserDto().getPassword());
            userListMapper.insert(userList1);
        }
    }

}
