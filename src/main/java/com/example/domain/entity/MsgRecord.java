package com.example.domain.entity;

import lombok.Data;

import java.util.Map;

@Data
public class MsgRecord {
    private Long id;
    private String topic;
    private String tag;
    private String key;
    private String value;
}
