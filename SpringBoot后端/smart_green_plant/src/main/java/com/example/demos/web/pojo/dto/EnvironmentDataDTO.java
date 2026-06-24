package com.example.demos.web.pojo.dto;


import com.example.demos.web.pojo.vo.DailyEnvironmentDataVO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class EnvironmentDataDTO {

    private DailyEnvironmentDataVO dailyEnvironmentDataVO;

    private String plantName;

}
