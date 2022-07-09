package com.example.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.jboss.logging.LogMessage;

@Data
@AllArgsConstructor
public class AccountChangeMsg {
    //发送方
    private Long sUid;
    //收款人
    private Long tUid;
    //收款金额
    private int mount;
}
