package com.example.demos.web.pojo.vo;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class PlantInstanceVO {
    //植物实例id
    private Integer id;
    //用户种植的植物id
    private Integer plantId;
    private String plantName;
    private String location;
    private String deviceName;
    private String imgUrl;
    @JsonFormat(locale="zh",timezone="GMT+8", pattern="yyyy-MM-dd HH:mm:ss")
    private LocalDateTime datePlanted;
}
