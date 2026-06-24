package com.example.demos.web.pojo.vo;


import com.baomidou.mybatisplus.annotation.TableField;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class UserVO {
    private Integer id;
    private String username;
    private String nickName;
    private String sex;
    private String address;
    private String phone;
    private String email;
    private String avatar;
    private String diyBk;
    private String token;
}
