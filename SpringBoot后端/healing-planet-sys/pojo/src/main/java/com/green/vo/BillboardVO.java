package com.green.vo;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class BillboardVO {

    /**
     * 公告牌
     */
    private String content;

    /**
     * 公告时间
     */
    private Date createTime;

    /**
     * 1：展示中，0：过期
     */
    private boolean show = false;
}
