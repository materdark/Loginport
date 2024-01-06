package com.example.springboot_team.dto;

import lombok.Data;

@Data
public class UserDto {
    private Integer id;
    private String phoneNumber;
    private String username;
    private String password;
    private String fingerPrintJs;
}
