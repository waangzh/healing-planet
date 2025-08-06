package com.green.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder(toBuilder = true)
@TableName("user_purchase_tags")
public class UserPurchaseTags {
    //主键
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private String id;

    //绑定的设备id
    @TableField("device_id")
    private Integer deviceId;

    //是否购买设备
    @TableField("is_purchased")
    private Boolean isPurchased;

    //推荐标签
    //1. 购买就填上绿植种植者
    //2. 没有购买就填上绿植种植者
    @TableField("recommend_tags")
    private String recommendTags;


    //关联的社区用户的id
    //外键
    @TableField("community_user_id")
    private String communityUserId;

    //绿植后台用户id
    @TableField("back_end_user_id")
    private Integer backEndUserId;

    @TableField("device_key")
    private String deviceKey;

    //后台用户的账号
    @TableField("account")
    private String account;

}
