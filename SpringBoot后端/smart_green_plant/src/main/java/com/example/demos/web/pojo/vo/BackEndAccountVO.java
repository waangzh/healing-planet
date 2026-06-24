package com.example.demos.web.pojo.vo;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class BackEndAccountVO {
    private String account; //生成的账号
    private String password;//生成的密码
    private Integer deviceId;//设备id号
    private Integer backEndUserId;//后台生成的用户账号的id用于和社区用户做绑定
}
