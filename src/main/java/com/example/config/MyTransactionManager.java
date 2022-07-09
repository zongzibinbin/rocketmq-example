package com.example.config;

import com.example.service.MQProducer;
import org.apache.rocketmq.client.exception.MQBrokerException;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.remoting.exception.RemotingException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.DefaultTransactionStatus;

import javax.sql.DataSource;
@Component
public class MyTransactionManager extends DataSourceTransactionManager {
    @Autowired
    MQProducer mqProducer;
    public MyTransactionManager(DataSource dataSource){
        super(dataSource);
    }

    @Override
    protected void doCommit(DefaultTransactionStatus status) {
        try {
            super.doCommit(status);
        }finally {
            //事务提交后
            sendMQ();
        }
    }

    private void sendMQ() {
        try {
            //发送ThreadLocal内所有待发送的mq
            mqProducer.sendAllThreadLocalMsg();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
