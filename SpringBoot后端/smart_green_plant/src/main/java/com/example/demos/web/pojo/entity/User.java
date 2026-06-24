package com.example.demos.web.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

//这个类被@Data注解修饰，该注解是Lombok提供的，它会自动生成所有的getter和setter方法、
// equals和hashCode方法、toString方法等
@TableName("user")
@Data//不包括无参构造和全参构造
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class User {
    @TableId(type = IdType.AUTO)
    private Integer id;

    private String username;
    private String nickName;
    private String password;
    private String sex;
    private String address;
    private String phone;
    private String email;
    private String avatar; // 头像
    private String diyBk; // 用户自定义背景图片
    @TableField(exist = false)  //表中没有token不会报错仍能编译运行
    private String token;

}
