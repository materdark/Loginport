package com.example.springboot_team.kafka.Consumer;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.springboot_team.kafka.domain.MessageMock;
import com.example.springboot_team.mapper.user_listMapper;
import com.example.springboot_team.pojo.user_list;
import com.example.springboot_team.utils.MD5Util;
import jakarta.annotation.Resource;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Transactional
@Service
public class KafkaPasswordChangeConsumer {
    @Resource
    private user_listMapper userListMapper;
    @Resource
    private RedissonClient redissonClient;
    //数据库连接对象应该是重用对象，这样子可以提升代码的性能
    private LambdaQueryWrapper<user_list> lambdaQueryWrapper=new LambdaQueryWrapper<>();
    private  static  final ExecutorService CACHE_REBUILD_EXECUTOR= Executors.newFixedThreadPool(100);
    @KafkaListener(topics = "passwordChangeTopic", groupId = "my-group-id")
    public void consume(String message){
        //将获取的Json对象进行反序列化操作
        MessageMock messageMock= JSONUtil.toBean(message, MessageMock.class);
        RLock lock = redissonClient.getLock("lock:mysql:" + messageMock.getUserChangeDto().getUsername());
        boolean isLock = lock.tryLock();
        if (isLock) {
            //6.3成功，开启独立线程，实现缓存重建
            CACHE_REBUILD_EXECUTOR.submit(()->{
                try {
                    //清除之前的查询条件，否则会拼接到之前的条件
                    lambdaQueryWrapper.clear();
                    //找到对应的应该更新的账号
                    lambdaQueryWrapper.eq(user_list::getUsername,messageMock.getUserChangeDto().getUsername());
                    user_list userPasswordChange = userListMapper.selectOne(lambdaQueryWrapper);
                    //更新密码
                    userPasswordChange.setPassword(MD5Util.encrypt(messageMock.getUserChangeDto().getNew_password()));
                    userListMapper.update(userPasswordChange,lambdaQueryWrapper);
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
}
