package com.example.demos.web.pojo.entity;

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
public class DetectInfo {
    Integer id;//主键id
    String plantName;//植物名称
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    LocalDateTime detectionTime;//模型检测时间
    Integer deviceId;//设备id
    String location;//设备地址
    String detectionResult;//检测结果
    String status;//检测结果是否可信：”正常“ “存疑”
    String suggestion;//“智能”建议
    String detectionImageUrl;//检测图片

}
