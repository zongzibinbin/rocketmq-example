package com.example.service;

import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.support.hsf.HSFJSONUtils;
import com.example.domain.entity.MsgRecord;
import com.example.mapper.MsgRecordMapper;
import org.apache.rocketmq.client.exception.MQBrokerException;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.client.producer.SendStatus;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.remoting.exception.RemotingException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Component
public class MQProducer {
    private ThreadLocal<List<Long>> threadLocal=new ThreadLocal<>();
    @Autowired
    DefaultMQProducer defaultMQProducer;
    @Autowired
    MsgRecordMapper msgRecordMapper;

    public SendResult send(String topic, Object value){
        try {
            return defaultMQProducer.send(new Message(topic, JSONObject.toJSONString(value).getBytes()));
        }catch (Exception e){
        }
        return null;
    }

    /**
     * 消息存进数据库，缓存在threadLocal中，事务提交后再发送消息
     */
    public void sendAfterCommit(String topic,Object value){
        MsgRecord msgRecord =new MsgRecord();
        msgRecord.setTopic(topic);
        msgRecord.setValue(JSONObject.toJSONString(value));
        msgRecordMapper.insert(msgRecord);
        List<Long> msgIds = threadLocal.get();
        if(CollectionUtils.isEmpty(msgIds)){
            msgIds=new ArrayList<>();
        }
        msgIds.add(msgRecord.getId());
    }

    /**
     * 事务提交后调用该方法
     */
    public void sendAllThreadLocalMsg() throws InterruptedException, RemotingException, MQClientException, MQBrokerException {
        List<Long> msgIds = threadLocal.get();
        if(CollectionUtils.isEmpty(msgIds)){
           return;
        }
        List<MsgRecord> msgRecords = msgRecordMapper.selectBatchIds(msgIds);
        for (MsgRecord msgRecord : msgRecords) {
            SendResult send = defaultMQProducer.send(new Message(msgRecord.getTopic(), msgRecord.getTag(), msgRecord.getKey(), msgRecord.getValue().getBytes()));
            if(SendStatus.SEND_OK.equals(send.getSendStatus())){
                //发送成功一条就删一条消息，这样数据库表也不会变大
                msgRecordMapper.deleteById(msgRecord.getId());
            }else {
                //发送失败就等待下次重试，并将消息保留在表中。
                msgRecordMapper.retryLater(msgRecord.getId());
            }
        }
    }
}
