package com.example.springboot_team.listener;

import com.example.springboot_team.dto.UserDto;
import com.example.springboot_team.mapper.user_listMapper;
import com.example.springboot_team.pojo.user_list;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

@Slf4j
@Component
public class RocketEntityMessageListenerS {
    @Resource
    private user_listMapper userListMapper;

    // topic需要和生产者的topic一致，consumerGroup属性是必须指定的，内容可以随意
    // selectorExpression的意思指的就是tag，默认为“*”，不设置的话会监听所有消息
    // 注意：这个ConsumerSend2和上面ConsumerSend在没有添加tag做区分时，不能共存，
    // 不然生产者发送一条消息，这两个都会去消费，如果类型不同会有一个报错，所以实际运用中最好加上tag，写这只是让你看知道就行
    @Component
    @RocketMQMessageListener(topic = "bootTestTopicY", consumerGroup = "Con_Group_Two")
    public class ConsumerSend2 implements RocketMQListener<MessageExt> {
        @Override
        public void onMessage(MessageExt messageExt) {
            System.out.println("我是消费者");
        }
    }
}
