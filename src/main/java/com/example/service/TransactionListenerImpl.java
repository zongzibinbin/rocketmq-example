package com.example.service;

import com.alibaba.fastjson.JSONObject;
import com.example.domain.dto.AccountChangeMsg;
import com.example.mapper.AccountChangeRecordMapper;
import org.apache.rocketmq.client.producer.LocalTransactionState;
import org.apache.rocketmq.client.producer.TransactionListener;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.spring.annotation.RocketMQTransactionListener;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Objects;

@RocketMQTransactionListener(txProducerGroup = "tx_order")
public class TransactionListenerImpl implements TransactionListener {
    @Autowired
    Reliable3 reliable3;
    @Autowired
    AccountChangeRecordMapper accountChangeRecordMapper;
    @Override
    public LocalTransactionState executeLocalTransaction(Message message, Object o) {
        try {
            AccountChangeMsg accountChangeMsg = JSONObject.parseObject(message.getBody(), AccountChangeMsg.class);
            reliable3.executeLocalTransaction(accountChangeMsg,message.getTransactionId());
        }catch (Exception e){
            return LocalTransactionState.UNKNOW;
        }
        return LocalTransactionState.COMMIT_MESSAGE;
    }

    @Override
    public LocalTransactionState checkLocalTransaction(MessageExt messageExt) {
        //转账记录存在说明事务执行成功，可以发送消息。
        return Objects.nonNull(accountChangeRecordMapper.getByTransactionId(messageExt.getTransactionId()))
                ? LocalTransactionState.COMMIT_MESSAGE
                :LocalTransactionState.ROLLBACK_MESSAGE;
    }
}
