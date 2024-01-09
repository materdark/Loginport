package com.example.springboot_team.kafka.utils;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.rocketmq.logging.org.slf4j.Logger;
import org.apache.rocketmq.logging.org.slf4j.LoggerFactory;
import org.springframework.kafka.support.ProducerListener;
import org.springframework.stereotype.Component;
/**
 * 生产者失败的回调函数检验
 * */
@Component
public class KafkaSendResultHandler implements ProducerListener {

    private static final Logger log = LoggerFactory.getLogger(KafkaSendResultHandler.class);

    @Override
    public void onSuccess(ProducerRecord producerRecord, RecordMetadata recordMetadata) {
        log.info("Message send success : " + producerRecord.toString());
    }

    @Override
    public void onError(ProducerRecord producerRecord, RecordMetadata recordMetadata, Exception exception) {
        log.info("Message send error : " + producerRecord.toString());
    }
}
