package com.shinhan.dongibuyeo.domain.member.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
@AllArgsConstructor
public class MemberLoginResponse {

    private UUID id;
    private String email;
    private String name;
    private String nickname;
    private String profileImage;
    private String apiKey;
    private String deviceToken;

}
