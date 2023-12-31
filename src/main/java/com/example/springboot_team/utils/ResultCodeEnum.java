package com.example.springboot_team.utils;

/**
 * 统一返回结果状态信息类
 *
 */
public enum ResultCodeEnum {

    SUCCESS(200,"测试成功"),
    USERNAME_ERROR(501,"用户名错误"),
    PASSWORD_ERROR(503,"密码错误"),
    NOTLOGIN(504,"notLogin"),
    USERNAME_USED(505,"用户已经存在"),
    PHONE_ERROR(403,"手机格式错误"),
    SERVE_ERROR(404,"存在异常"),
    CODE_ERROR(409,"验证码错误");

    private Integer code;
    private String message;
    private ResultCodeEnum(Integer code, String message) {
        this.code = code;
        this.message = message;
    }
    public Integer getCode() {
        return code;
    }
    public String getMessage() {
        return message;
    }
}
