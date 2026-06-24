package com.example.demos.web.pojo.vo;

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
public class DetectInfoVO {
    String plantName;//植物信息
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    LocalDateTime detectionTime;//检测时间
    String suggestion;//给出的建议
    String detectionResult;//检测结果
    String detectionImageUrl;//检测结果的图片
    Integer deviceId;//设备id
    String location;//设备地点

}
