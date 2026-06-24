package com.example.demos.web.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.demos.web.common.result.Result;
import com.example.demos.web.pojo.dto.DataAnalysisDTO;
import com.example.demos.web.pojo.dto.EnvironmentDataPageQueryDTO;
import com.example.demos.web.pojo.dto.PlantInstanceDTO;
import com.example.demos.web.pojo.entity.EnvironmentData;
import com.example.demos.web.pojo.vo.DailyEnvironmentDataVO;
import com.example.demos.web.service.EnvironmentDataService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@RestController
@RequestMapping
public class EnvironmentDataController {
    @Autowired
    private EnvironmentDataService environmentDataService;


    /**
     * 获取days天12点左右的环境数据
     * @return
     */
    @GetMapping("/getSevenDayData")
    public Result<?> getDailyDataAroundNoon(@RequestParam Long plantInstanceId,@RequestParam Integer days){
        log.info("获取近七天12点左右的环境数据");
        List<DailyEnvironmentDataVO> dailyEnvironmentDataVO = environmentDataService.getDailyDataAroundNoon(plantInstanceId,days);
        return Result.success(dailyEnvironmentDataVO);
    }



    /**
     * 分页查询历史环境数据
     * @param environmentDataPageQueryDTO
     * @return
     */
    @PostMapping("/history-data")
    public Result<?> getEnvironmentData(@RequestBody EnvironmentDataPageQueryDTO environmentDataPageQueryDTO) {
        log.info("分页查询历史环境数据:{}",environmentDataPageQueryDTO);
        if(environmentDataPageQueryDTO.getPage()==0){
            environmentDataPageQueryDTO.setPage(1);
        }
        if (environmentDataPageQueryDTO.getPageSize()==0){
            environmentDataPageQueryDTO.setPageSize(10);
        }
        if(environmentDataPageQueryDTO.getEndDate() == null){
            environmentDataPageQueryDTO.setEndDate(LocalDateTime.now());
        }
        Page<EnvironmentData> data = environmentDataService.getEnvironmentDataPage(environmentDataPageQueryDTO);

        return Result.success(data);
    }

    /**
     * 导出环境数据
     * @param plantInstanceDTO
     * @return
     * @throws IOException
     */
    @PostMapping("/export")
    public Result<String> export(@RequestBody PlantInstanceDTO plantInstanceDTO) throws IOException {
        log.info("导出识别记录");
        String content =  environmentDataService.export(plantInstanceDTO);
        return Result.success(content);
    }


    /**
     * 根据历史数据分析植物健康状况
     * @param analysisDTO
     * @return
     */
    @PostMapping("/analysis")
    public Result<?> dataAnalysis(@RequestBody DataAnalysisDTO analysisDTO){
        log.info("根据历史数据分析植物健康状况:{}",analysisDTO);
        String analyis = environmentDataService.analysis(analysisDTO);
        return Result.success(analyis);
    }
}
