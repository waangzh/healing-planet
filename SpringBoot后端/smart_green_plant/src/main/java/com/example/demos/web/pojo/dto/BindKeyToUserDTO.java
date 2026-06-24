package com.example.demos.web.pojo.dto;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;


@AllArgsConstructor
@NoArgsConstructor
@Slf4j
@Builder
@Data
public class BindKeyToUserDTO {
    //社区用户id
    private String communityUserId;
    // 前端传递过来的设备uuid
    private String deviceKey;
}
