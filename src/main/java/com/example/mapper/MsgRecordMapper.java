package com.example.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.domain.entity.MsgRecord;
import com.example.domain.entity.UserAccount;

public interface MsgRecordMapper extends BaseMapper<MsgRecord> {

    /**
     * 设置消息1分钟后待重试
     * @param id
     */
    void retryLater(Long id);
}
