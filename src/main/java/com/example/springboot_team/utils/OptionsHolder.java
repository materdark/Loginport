package com.example.springboot_team.utils;

import com.example.springboot_team.dto.FlagDto;
import com.example.springboot_team.dto.UserDto;

public class OptionsHolder {
    private static final ThreadLocal<FlagDto> tl = new ThreadLocal<>();

    public static void saveFlag(FlagDto flag){
        tl.set(flag);
    }

    public static FlagDto getFlag(){
        return tl.get();
    }

    public static void removeFlag(){
        tl.remove();
    }
}
