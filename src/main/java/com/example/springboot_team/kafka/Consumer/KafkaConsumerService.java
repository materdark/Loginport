package com.example.springboot_team.kafka.Consumer;

import cn.hutool.json.JSONUtil;
import com.example.springboot_team.kafka.domain.MessageMock;
import com.example.springboot_team.mapper.user_listMapper;
import com.example.springboot_team.pojo.user_list;
import jakarta.annotation.Resource;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
/**
 * 消费者负责监听生产者发送过来的数据
 * */
@Service
public class KafkaConsumerService {
    @Resource
    private user_listMapper userListMapper;
    @KafkaListener(topics = "my-topic", groupId = "my-group-id")
    public void consume(String message) {
        //将获取的Json对象进行反序列化操作
        MessageMock messageMock=JSONUtil.toBean(message, MessageMock.class);
        user_list userList=new user_list();
        userList.setUsername(messageMock.getUserDto().getUsername());
        userList.setPassword(messageMock.getUserDto().getPassword());
        userListMapper.insert(userList);
    }

}
