package com.example.demo.dto.exchange;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MessageRequest {

    private String content;
    private String imageUrl;
    private String clientMessageKey;
}
