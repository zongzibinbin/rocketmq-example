package com.example.dao;

import com.baomidou.mybatisplus.extension.service.IService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.domain.entity.UserAccount;
import com.example.mapper.UserAccountMapper;

public class UserAccountDao extends ServiceImpl<UserAccountMapper,UserAccount> {

    public void addAmount(Long uid,Integer amount){

    }
}
