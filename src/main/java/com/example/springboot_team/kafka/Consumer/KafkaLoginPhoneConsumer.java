package com.example.springboot_team.kafka.Consumer;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.springboot_team.kafka.domain.MessageMock;
import com.example.springboot_team.mapper.user_phoneMapper;
import com.example.springboot_team.pojo.user_phone;
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
public class KafkaLoginPhoneConsumer {
    @Resource
    private user_phoneMapper userPhoneMapper;
    @Resource
    private RedissonClient redissonClient;
    //数据库连接对象应该是重用对象，这样子可以提升代码的性能
    private LambdaQueryWrapper<user_phone> lambdaQueryWrapper=new LambdaQueryWrapper<>();
    private  static  final ExecutorService CACHE_REBUILD_EXECUTOR= Executors.newFixedThreadPool(100);
    @KafkaListener(topics = "loginPhoneTopic", groupId = "my-group-id")
    public void consume(String message){
        //将获取的Json对象进行反序列化操作
        MessageMock messageMock= JSONUtil.toBean(message, MessageMock.class);
        RLock lock = redissonClient.getLock("lock:order:" + messageMock.getUserPhone().getPhonenumber());
        boolean isLock = lock.tryLock();
        if (isLock) {
            //6.3成功，开启独立线程，实现缓存重建
            CACHE_REBUILD_EXECUTOR.submit(()->{
                try {
                    //清除之前的查询条件
                    lambdaQueryWrapper.clear();
                    //为了防止消费堆积，导致数据库重复插入数据，插入前必须先检验一下数据库中是否有账号，如果没有就插入数据库
                    lambdaQueryWrapper.eq(user_phone::getPhonenumber,messageMock.getUserPhone().getPhonenumber());
                    user_phone userPhone=userPhoneMapper.selectOne(lambdaQueryWrapper);
                    if(userPhone==null){
                        //之所以要新建一个，而不是用之前的userList
                        // 是因为我发现除了初始化的对象以外，其他对象比如上个userList==null时，空对象使用方法会自动结束
                        //这触发了java底层垃圾回收机制，这属于空对象模式
                        user_phone userPhone1=new user_phone();
                        userPhone1.setPhonenumber(messageMock.getUserPhone().getPhonenumber());
                        userPhoneMapper.insert(userPhone1);
                    }
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
