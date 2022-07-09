package com.example.service;

import com.example.domain.dto.AccountChangeMsg;
import com.example.mapper.UserAccountMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/**
 * 本地入库消息记录，来保证消息可靠
 */
public class Reliable2 {
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
        //事务结束后发送消息
        mqProducer.sendAfterCommit("topic",new AccountChangeMsg(sUid, tUid, amount));
    }

}
