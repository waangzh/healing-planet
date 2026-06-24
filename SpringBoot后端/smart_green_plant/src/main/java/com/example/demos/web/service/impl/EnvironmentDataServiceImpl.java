package com.example.demos.web.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.demos.web.constant.MessageConstant;
import com.example.demos.web.exception.CustomException;
import com.example.demos.web.mapper.DeviceMapper;
import com.example.demos.web.mapper.EnvironmentDataMapper;
import com.example.demos.web.mapper.PlantInstanceMapper;
import com.example.demos.web.mapper.PlantMapper;
import com.example.demos.web.pojo.dto.DataAnalysisDTO;
import com.example.demos.web.pojo.dto.EnvironmentDataPageQueryDTO;
import com.example.demos.web.pojo.dto.PlantInstanceDTO;
import com.example.demos.web.pojo.entity.*;
import com.example.demos.web.pojo.vo.*;
import com.example.demos.web.service.EnvironmentDataService;
import com.example.demos.web.utils.BaiDuUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Slf4j

public class EnvironmentDataServiceImpl implements EnvironmentDataService {
    @Autowired
    EnvironmentDataMapper environmentDataMapper;
    @Qualifier("plantMapper")
    @Autowired
    private PlantMapper plantMapper;
    @Autowired
    private BaiDuUtil baiDuUtil;
    @Qualifier("plantInstanceMapper")
    @Autowired
    private PlantInstanceMapper plantInstanceMapper;
    @Qualifier("deviceMapper")
    @Autowired
    private DeviceMapper deviceMapper;

    /**
     * 获取七天12点左右的环境数据
     * @return
     */
    @Override
    public List<DailyEnvironmentDataVO> getDailyDataAroundNoon(Long plantInstanceId,Integer days) {
        // 获取当前时间和days天前的时间
        LocalDateTime endDate = LocalDateTime.now();
        LocalDateTime startDate = endDate.minusDays(days);

        // 构建查询条件
        //and 和 or 的优先级不同，会导致查询条件被错误地解析。and优先级更高
        /*
        plant_instance_id = ?
        and recorded_time between ? and ?
        and (
            (hour(recorded_time) = 11 and minute(recorded_time) >= 50)
            or
            (hour(recorded_time) = 12 and minute(recorded_time) <= 10)
            )
        */
        QueryWrapper<EnvironmentData> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("plant_instance_id", plantInstanceId)  // 根据 plantInstanceId 过滤
                .between("recorded_time", startDate, endDate)  // 时间范围
                .apply("((hour(recorded_time) = 11 and minute(recorded_time) >= 50) or " +
                        "(hour(recorded_time) = 12 and minute(recorded_time) <= 10))");  // 查询每天 11:50 到 12:10 的数据

        // 执行查询
        List<EnvironmentData> dataList = environmentDataMapper.selectList(queryWrapper);

        // 按天分组，每天只取一条最接近 12:00 的数据
        Map<LocalDate, EnvironmentData> closestDataMap = new HashMap<>();
        for (EnvironmentData data : dataList) {
            LocalDate date = data.getRecordedTime().toLocalDate();
            EnvironmentData existingData = closestDataMap.get(date);

            // 计算当前数据与 12:00 的差值
            long currentDiff = Math.abs(data.getRecordedTime().getMinute()); // 分钟差值
            //如果数据不存在，则放入
            if (existingData == null) {
                closestDataMap.put(date, data);
            } else {
                // 计算已有数据与 12:00 的差值
                long existingDiff = Math.abs(existingData.getRecordedTime().getMinute());
                // 如果当前数据更接近 12:00，则替换
                if (currentDiff < existingDiff) {
                    closestDataMap.put(date, data);
                }
            }
        }

        // 将查询结果转换为 DailyEnvironmentDataVO
        List<DailyEnvironmentDataVO> voList = closestDataMap.values().stream()
                .sorted(Comparator.comparing(EnvironmentData::getRecordedTime))  // 按时间升序
                .map(data -> new DailyEnvironmentDataVO(
                        data.getTemperature(),
                        data.getHumidity(),
                        data.getCo2Concentration(),
                        data.getSoilMoisture(),
                        data.getLightIntensity(),
                        data.getRecordedTime()))
                .collect(Collectors.toList());

        return voList;
    }

    /**
     * 分页查询历史环境数据
     * @param pageQueryDTO
     * @return
     */
    @Override
    public Page<EnvironmentData> getEnvironmentDataPage(EnvironmentDataPageQueryDTO pageQueryDTO) {
        // 创建分页对象
        Page<EnvironmentData> page = new Page<>(pageQueryDTO.getPage(), pageQueryDTO.getPageSize());
        QueryWrapper<EnvironmentData> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("plant_instance_id", pageQueryDTO.getPlantInstanceId());

        // 根据日期范围查询
        if (pageQueryDTO.getStartDate() != null && pageQueryDTO.getEndDate() != null) {
            queryWrapper.between("recorded_time", pageQueryDTO.getStartDate(), pageQueryDTO.getEndDate());
        } else if (pageQueryDTO.getStartDate() != null) {
            queryWrapper.ge("recorded_time", pageQueryDTO.getStartDate());
        } else if (pageQueryDTO.getEndDate() != null) {
            queryWrapper.le("recorded_time", pageQueryDTO.getEndDate());
        }

        return environmentDataMapper.selectPage(page, queryWrapper);
    }

    /**
     * 导出环境数据
     * @return
     */
    @Override
    public String export(PlantInstanceDTO plantInstanceDTO) throws IOException {
        // 创建一个工作簿
        XSSFWorkbook workbook = new XSSFWorkbook();
        XSSFSheet sheet = workbook.createSheet("历史环境数据");

        // 创建表头
        Row headerRow = sheet.createRow(0);
        headerRow.createCell(0).setCellValue("设备名称");
        headerRow.createCell(1).setCellValue("绿植种类");
        headerRow.createCell(2).setCellValue("空气湿度（%）");
        headerRow.createCell(3).setCellValue("温度（℃）");
        headerRow.createCell(4).setCellValue("光照强度（Lx）");
        headerRow.createCell(5).setCellValue("二氧化碳含量（ppm）");
        headerRow.createCell(6).setCellValue("土壤湿度（%）");
        headerRow.createCell(7).setCellValue("记录时间");


        // 获取数据并填充表格
        QueryWrapper<EnvironmentData> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("plant_instance_id", plantInstanceDTO.getId());
        Integer deviceId = plantInstanceMapper.selectById(plantInstanceDTO.getId()).getDeviceId();
        String deviceName = deviceMapper.selectById(deviceId).getName();
        List<EnvironmentData> records = environmentDataMapper.selectList(queryWrapper);
        log.info("导出时，获取环境数据:{}",records);
        int rowIndex = 1;
        for (EnvironmentData record : records) {
            XSSFRow row = sheet.createRow(rowIndex++);
            row.createCell(0).setCellValue(deviceName); // 设备名称
            String plantName = plantMapper.selectById(plantInstanceDTO.getPlantId()).getName(); // 植物种类名称
            row.createCell(1).setCellValue(plantName);
            // 记录时间
            if(record.getRecordedTime() == null)
            {
                throw new CustomException("-1", MessageConstant.RECORDS_EXIST_NULL);
            }
            else
                row.createCell(7).setCellValue(record.getRecordedTime().toString());
            // 空气湿度
            if(record.getHumidity() == null)
            {
                //throw new CustomException("-1",MessageConstant.RECORDS_EXIST_NULL);
                row.createCell(2).setCellValue(-1);
            }
            else
                row.createCell(2).setCellValue(record.getHumidity());
            // 温度
            if(record.getTemperature() == null)
            {
                //throw new CustomException("-1",MessageConstant.RECORDS_EXIST_NULL);
                row.createCell(3).setCellValue(-1);

            }
            else
                row.createCell(3).setCellValue(record.getTemperature());
            // 光照强度
            if(record.getLightIntensity() == null)
            {
                //throw new CustomException("-1",MessageConstant.RECORDS_EXIST_NULL);
                row.createCell(3).setCellValue(-1);
            }
            else
                row.createCell(4).setCellValue(record.getLightIntensity());
            // 二氧化碳含量
            if(record.getCo2Concentration() == null)
            {
                //throw new CustomException("-1",MessageConstant.RECORDS_EXIST_NULL);
                row.createCell(3).setCellValue(-1);
            }
            else
                row.createCell(5).setCellValue(record.getCo2Concentration());
            // 土壤湿度
            if(record.getSoilMoisture() == null){
                //throw new CustomException("-1",MessageConstant.RECORDS_EXIST_NULL);
                row.createCell(3).setCellValue(-1);
            }
            else{
                row.createCell(6).setCellValue(record.getSoilMoisture());
            }

        }

        log.info("表格内容:{}",sheet);
        // 将 Excel 内容写入 ByteArrayOutputStream
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        workbook.write(outputStream);
        workbook.close();

        byte[] content = outputStream.toByteArray();
        log.info("导出文件的大小:{}",content.length);
        String base64Data = "data:application/vnd.openxmlformats-officedocument.spreadsheetml.sheet;base64,"
                + java.util.Base64.getEncoder().encodeToString(content);


        return base64Data;
    }

    /**
     * 根据历史数据分析植物健康状况
     * @param analysisDTO
     * @return
     */
    @Override
    public String analysis(DataAnalysisDTO analysisDTO) {
        String plantName = plantMapper.selectById(analysisDTO.getPlantId()).getName();

        // 获取days天中每天的各环境数据的平均值
        LocalDateTime startDate = LocalDateTime.now().minusDays(analysisDTO.getAnalysisDays());
        List<Map<String, Object>> dailyDataList = environmentDataMapper.getDailyAverageData(analysisDTO.getPlantInstanceId(), startDate);

        //LambdaQueryWrapper<EnvironmentData> queryWrapper = new LambdaQueryWrapper<>();
        //queryWrapper.eq(EnvironmentData::getPlantInstanceId, analysisDTO.getPlantInstanceId());
        //List<EnvironmentData> environmentDataList = environmentDataMapper.selectList(queryWrapper);
        log.info("分析数据记录:{}",dailyDataList);
        String analysisResult = "";
        try {
            analysisResult = baiDuUtil.analyis(plantName,dailyDataList,analysisDTO.getPlantImg());
            // 解析 JSON
            JSONObject outerJson = new JSONObject(analysisResult);
            //log.info("outerjson:{}",outerJson);
            // 获取响应
            JSONArray choices = outerJson.getJSONArray("choices");
            if (choices.length() > 0) {
                JSONObject firstChoice = choices.getJSONObject(0);
                JSONObject message = firstChoice.getJSONObject("message");
                String result = message.getString("content");
                log.info("分析完成:{}",result);
                return result;
            } else {
                throw new RuntimeException("API响应中没有choices内容");
            }
        } catch (Exception e) {
            e.printStackTrace();
            return "生成养护建议时出错: " + e.getMessage();
        }
    }


}
