package com.example.demos.web.pojo.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class DataAnalysisDTO {
    /**
     * 植物实例id
     */
    private String plantInstanceId;
    /**
     * 植物种类id
     */
    private String plantId;
    /**
     * 用户植物照片
     */
    private String plantImg;

    private Integer analysisDays;
}
