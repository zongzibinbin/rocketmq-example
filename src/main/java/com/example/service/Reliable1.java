package com.example.service;

import com.example.domain.dto.AccountChangeMsg;
import com.example.domain.entity.UserAccount;
import com.example.mapper.UserAccountMapper;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;

/**
 * 简单保证消息可靠投递。
 */
public class Reliable1 {
    @Autowired
    MQProducer mqProducer;
    @Autowired
    UserAccountMapper userAccountMapper;

    /**
     * A给B转账 100元，转账后发送消息给B。
     * @param sUid
     * @param tUid
     * @param amount
     */
    @Transactional
    public void transfer(Long sUid,Long tUid,int amount){
        //给A-100
        userAccountMapper.addAmount(sUid,-amount);
        //给B+100
        userAccountMapper.addAmount(tUid,amount);
        //发送消息,
        mqProducer.send("topic",new AccountChangeMsg(sUid, tUid, amount));
    }

}
