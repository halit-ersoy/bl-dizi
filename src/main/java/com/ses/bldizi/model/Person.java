package com.ses.bldizi.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Person {
    private UUID id;
    private String nickname;
    private String name;
    private String surname;
    private String email;
    private String password;

    @JsonProperty("isBanned")
    private boolean isBanned;
    private String banReason;
    private String role;
    private boolean allowMessages = true;

    @JsonProperty("isVerified")
    private boolean isVerified = false;
    private String cookie;
}
