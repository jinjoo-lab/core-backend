package com.shinhan.dongibuyeo.domain.chat.dto.request;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class MessageRequest {
    private UUID memberId;
    private String nickName;
    private String roomName;
    private String message;
    private String image;
    private String sendAt;
}
