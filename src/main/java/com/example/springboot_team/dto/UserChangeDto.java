package com.example.springboot_team.dto;

import lombok.Data;

@Data
public class UserChangeDto {
    private String username;
    private String old_password;
    private String new_password;
}
