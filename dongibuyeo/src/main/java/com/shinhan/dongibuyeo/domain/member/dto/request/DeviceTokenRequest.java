package com.shinhan.dongibuyeo.domain.member.dto.request;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class DeviceTokenRequest {
    private UUID memberId;
    private String deviceToken;
}