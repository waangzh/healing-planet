package com.example.demos.web.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;


@TableName("plant")
@Data
public class Plant {
    //绿植id
    @TableId(type = IdType.AUTO)
    private Integer id;
    private String name;
    private String imgUrl;
    private String careInstructions;
}
