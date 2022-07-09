package com.example.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.domain.entity.AccountChangeRecord;
import com.example.domain.entity.MsgRecord;

public interface AccountChangeRecordMapper extends BaseMapper<AccountChangeRecord> {

    /**
     * 设置消息1分钟后待重试
     * @param id
     */
    void retryLater(Long id);

    AccountChangeRecord getByTransactionId(String transactionId);
}
