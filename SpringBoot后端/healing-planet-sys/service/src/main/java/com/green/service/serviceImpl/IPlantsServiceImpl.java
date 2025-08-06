package com.green.service.serviceImpl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.green.common.exception.ApiException;
import com.green.dto.PlantDTO;
import com.green.entity.PlantCareGuides;
import com.green.entity.Plants;
import com.green.mapper.PlantCareGuidesMapper;
import com.green.mapper.PlantsMapper;
import com.green.service.IPlantsService;
import com.green.utils.BaiDuUtil;
import com.green.vo.PlantsVO;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class IPlantsServiceImpl extends ServiceImpl<PlantsMapper, Plants> implements IPlantsService {

    @Autowired
    private PlantsMapper plantsMapper;
    @Autowired
    private PlantCareGuidesMapper plantCareGuidesMapper;
    @Autowired
    private BaiDuUtil baiDuUtil;

    /**
     * 分页查询植物信息库
     * @param objectPage
     * @param key
     * @return
     */
    @Override
    public Page<PlantsVO> getList(Page<Object> page, String key) {
        Page<PlantsVO> ipage = this.baseMapper.selectListAndPage(page,key);
        return ipage;
    }

    /**
     * 根据绿植id查询
     * @param id
     * @return
     */
    @Override
    public PlantsVO getPlantsById(String id) {
        return  plantsMapper.selectById(id);
    }

    /**
     * 添加植物
     * @param plantDTO
     */
    @Override
    public void add(PlantDTO plantDTO) {
        Plants p = this.getOne(new LambdaQueryWrapper<Plants>().eq(Plants::getCommonName,plantDTO.getCommonName()));
        if(p != null){
            throw new ApiException("该植物已添加，请勿重复操作");
        }
        Plants plants = Plants.builder()
                .commonName(plantDTO.getCommonName())
                .scientificName(plantDTO.getScientificName())
                .coverImg(plantDTO.getCoverImg())
                .createdAt(LocalDateTime.now())
                .build();

        this.save(plants);
        String plantId = plants.getId();
        PlantCareGuides plantCareGuides = PlantCareGuides.builder()
                .plantId(plantId)
                .detailAdvice(plantDTO.getDetailAdvice())
                .fertilizingTips(plantDTO.getFertilizingTips())
                .humidityPreference(plantDTO.getHumidityPreference())
                .temperaturePreference(plantDTO.getTemperaturePreference())
                .wateringFrequency(plantDTO.getWateringFrequency())
                .lightRequirements(plantDTO.getLightRequirements())
                .build();

        plantCareGuidesMapper.insert(plantCareGuides);
    }


    /**
     * 识别植物
     * @param imgUrl
     * @return
     */
    @Override
    public String identify(String imgUrl) {
        String result;
        try {
            result = baiDuUtil.identifyPlant(imgUrl);
            // 解析 JSON
            JSONObject outerJson = new JSONObject(result);

            // 获取响应
            JSONArray choices = outerJson.getJSONArray("choices");
            if (choices.length() > 0) {
                JSONObject firstChoice = choices.getJSONObject(0);
                JSONObject message = firstChoice.getJSONObject("message");
                return message.getString("content");
            } else {
                throw new RuntimeException("API响应中没有choices内容");
            }
        } catch (Exception e) {
            e.printStackTrace();
            return "生成养护建议时出错: " + e.getMessage();
        }
    }
}
