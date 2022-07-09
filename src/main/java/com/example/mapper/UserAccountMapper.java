package com.example.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.domain.entity.UserAccount;

public interface UserAccountMapper extends BaseMapper<UserAccount> {
    /**
     * 给账户加钱，太简单就不实现了
     * @param uid
     * @param amount
     */
    public void addAmount(Long uid,Integer amount);
}
