package com.example.demos.web.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.demos.web.common.result.Result;
import com.example.demos.web.pojo.dto.DataAnalysisDTO;
import com.example.demos.web.pojo.dto.EnvironmentDataPageQueryDTO;
import com.example.demos.web.pojo.dto.PlantInstanceDTO;
import com.example.demos.web.pojo.entity.EnvironmentData;
import com.example.demos.web.pojo.vo.DailyEnvironmentDataVO;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;

public interface EnvironmentDataService {


    /**
     * 获取七天12点左右的环境数据
     * @return
     */
    List<DailyEnvironmentDataVO> getDailyDataAroundNoon(Long plantInstanceId,Integer days);

    /**
     * 分页查询历史环境数据
     * @param pageNum
     * @param pageSize
     * @return
     */
    Page<EnvironmentData> getEnvironmentDataPage(EnvironmentDataPageQueryDTO environmentDataPageQueryDTO);

    /**
     * 导出环境数据
     * @return
     */
    String export(PlantInstanceDTO plantInstanceDTO)throws IOException;

    /**
     * 根据历史数据分析植物健康状况
     * @param analysisDTO
     * @return
     */
    String analysis(DataAnalysisDTO analysisDTO);
}
