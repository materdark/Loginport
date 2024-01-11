package com.example.springboot_team.utils.Interceptor;

import com.example.springboot_team.dto.FlagDto;
//用于存储本地线程

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
