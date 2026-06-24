package com.example.demos.web.service.impl;


import com.example.demos.web.mapper.DetectionMapper;
import com.example.demos.web.mapper.LocationMapper;
import com.example.demos.web.mapper.PlantInstanceMapper;
import com.example.demos.web.pojo.dto.DetectInfoDTO;
import com.example.demos.web.pojo.entity.DetectInfo;
import com.example.demos.web.pojo.entity.LocationData;
import com.example.demos.web.pojo.vo.DetectInfoVO;
import com.example.demos.web.service.DetectionService;
import com.example.demos.web.utils.AliOssUtil;
import com.example.demos.web.utils.BaiDuUtil;
import com.example.demos.web.utils.CodeTransUtil;
import com.example.demos.web.utils.HttpClientUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;


@Slf4j
@Service
public class DetectionServiceImpl implements DetectionService {
    @Autowired
    BaiDuUtil baiDuUtil;
    @Autowired
    DetectionMapper detectionMapper;
    @Autowired
    PlantInstanceMapper plantInstanceMapper;
    @Autowired
    LocationMapper locationMapper;

    public static Map<Integer, String> label_name = new HashMap<>();

    static {
        label_name.put(0, "Apple_Apple_scab (苹果苹果斑点病)");
        label_name.put(1, "Apple_Black_rot (苹果黑腐病)");
        label_name.put(2, "Apple_Cedar_apple_rust (苹果雪松锈病)");
        label_name.put(3, "Apple_healthy (健康苹果)");
        label_name.put(4, "Blueberry_healthy (健康蓝莓)");
        label_name.put(5, "Cherry_(including_sour)_Powdery_mildew (樱桃(包括酸樱桃)白粉病)");
        label_name.put(6, "Cherry_(including_sour)_healthy (健康樱桃)");
        label_name.put(7, "Corn_(maize)_Cercospora_leaf_spot_Gray_leaf_spot (玉米(玉米)灰斑病)");
        label_name.put(8, "Corn_(maize)_Common_rust_ (玉米(玉米)常见锈病)");
        label_name.put(9, "Corn_(maize)_Northern_Leaf_Blight (玉米(玉米)北方叶斑病)");
        label_name.put(10, "Corn_(maize)_healthy (健康玉米)");
        label_name.put(11, "Grape_Black_rot (葡萄黑腐病)");
        label_name.put(12, "Grape_Esca_(Black_Measles) (葡萄死株病(黑梅毒))");
        label_name.put(13, "Grape_Leaf_blight_(Isariopsis_Leaf_Spot) (葡萄叶斑病)");
        label_name.put(14, "Grape_healthy (健康葡萄)");
        label_name.put(15, "Orange_Haunglongbing_(Citrus_greening) (橙黄龙病)");
        label_name.put(16, "Peach_Bacterial_spot (桃子细菌斑点病)");
        label_name.put(17, "Peach_healthy (健康桃子)");
        label_name.put(18, "Pepper,_bell_Bacterial_spot (甜椒细菌斑点病)");
        label_name.put(19, "Pepper,_bell_healthy (健康甜椒)");
        label_name.put(20, "Potato_Early_blight (马铃薯早期枯萎病)");
        label_name.put(21, "Potato_Late_blight (马铃薯晚期枯萎病)");
        label_name.put(22, "Potato_healthy (健康马铃薯)");
        label_name.put(23, "Raspberry_healthy (健康覆盆子)");
        label_name.put(24, "Soybean_healthy (健康大豆)");
        label_name.put(25, "Squash_Powdery_mildew (南瓜白粉病)");
        label_name.put(26, "Strawberry_Leaf_scorch (草莓叶枯病)");
        label_name.put(27, "Strawberry_healthy (健康草莓)");
        label_name.put(28, "Tomato_Bacterial_spot (番茄细菌斑点病)");
        label_name.put(29, "Tomato_Early_blight (番茄早期枯萎病)");
        label_name.put(30, "Tomato_Late_blight (番茄晚期枯萎病)");
        label_name.put(31, "Tomato_Leaf_Mold (番茄叶霉病)");
        label_name.put(32, "Tomato_Septoria_leaf_spot (番茄叶斑病)");
        label_name.put(33, "Tomato_Spider_mites_Two-spotted_spider_mite (番茄蜘蛛螨(两点蜘蛛螨))");
        label_name.put(34, "Tomato_Target_Spot (番茄靶斑病)");
        label_name.put(35, "Tomato_Tomato_Yellow_Leaf_Curl_Virus (番茄黄叶弯曲病毒)");
        label_name.put(36, "Tomato_Tomato_mosaic_virus (番茄马赛克病毒)");
        label_name.put(37, "Tomato_healthy (健康番茄)");
    }

    @Autowired
    private AliOssUtil aliOssUtil;


    /**
     * 病理检测接口
     *
     * @param detectInfoDTO
     * @param image
     * @return
     */

    @Transactional
    @Override
    public DetectInfoVO detectPlantDiseaseFromImage(DetectInfoDTO detectInfoDTO, MultipartFile image) throws Exception {

        // 构造请求，访问模型接口，拿到数据
        String url = "http://127.0.0.1:5000/classify";
        String response = HttpClientUtil.doPostFile(url, image, "image");
        response = CodeTransUtil.decodeUnicode(response);
        log.info("响应结果：{}", response);
        //使用jackon解析JSON字符串
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode rootNode = objectMapper.readTree(response);
        //提取名字和标签
        String diseaseName = rootNode.path("class_name").asText();
        Integer label = rootNode.path("label").asInt();
        String cropName = rootNode.path("crop_name").asText();
        //以防模型出现检测错误，需要先对比一下，植物和识别出的种类是否是一类植物，如果是在进行后续操作

        List<String> plantNameByDeviceId = plantInstanceMapper.getPlantNameByDeviceId(detectInfoDTO.getDeviceId());
        //TODO:目前一个设备只有一个植物，后面在加多个植物的功能
        String plantName = plantNameByDeviceId.get(0);

        //创建检测结果的实体对象
        DetectInfo detectInfo = new DetectInfo();
        detectInfo.setDetectionTime(LocalDateTime.now());
        BeanUtils.copyProperties(detectInfoDTO, detectInfo);


        detectInfo.setPlantName(plantName);
        //获取检测图片，并放入aliyunoss，获取其url
        //获取原始文件名
        String originalFilename = image.getOriginalFilename();
        //获取文件后缀 .png/.jpg
        String extension = null;
        if (originalFilename != null) {
            extension = originalFilename.substring(originalFilename.lastIndexOf("."));
        }
        //构造新文件名
        String objectName = UUID.randomUUID().toString() + extension;
        //文件的请求路径
        String fliePath = aliOssUtil.upload(image.getBytes(), objectName);
        detectInfo.setDetectionImageUrl(fliePath);
        //根据设备id获取设备位置信息
        LocationData locationByDeviceId = locationMapper.getLocationByDeviceId(detectInfoDTO.getDeviceId());
        String location = locationByDeviceId.getAddress();
        detectInfo.setLocation(location);
        String adviceFromBaiDu = null;


        //如果检测出来的作物和实际的作物不一样，那么就不更新状态，只是简单的返回一段护理建议
        if (!cropName.contains(plantName) ) {
            //不更新作物状态
            //将此次检测标记为异常
            detectInfo.setStatus("存疑");
            detectInfo.setDetectionResult("有潜在患病风险");
            //调用大模型生成建议
            adviceFromBaiDu = baiDuUtil.generatePreventDiseaseAdvice(true,
                    null, location, plantName);
            detectInfo.setSuggestion(adviceFromBaiDu);
        } else {
            //此次检测健康
            detectInfo.setStatus("正常");
            if (!label_name.get(label).contains("healthy")) {
                //更新作物状态
                plantInstanceMapper.updateHealthyCondition(diseaseName);
                detectInfo.setDetectionResult(diseaseName);
                //调用大模型生成建议
                adviceFromBaiDu = baiDuUtil.generatePreventDiseaseAdvice(false, diseaseName
                        , location, plantName);
                detectInfo.setSuggestion(adviceFromBaiDu);

            } else {
                //更新作物状态
                plantInstanceMapper.updateHealthyCondition(diseaseName);
                detectInfo.setDetectionResult("健康");
                adviceFromBaiDu = baiDuUtil.generatePreventDiseaseAdvice(true,
                        null, location, plantName);
                detectInfo.setSuggestion(adviceFromBaiDu);
            }


        }
        //更新作物状态，插入识别记录信息
        detectionMapper.insertDetectInfo(detectInfo);
        DetectInfoVO detectInfoVO = new DetectInfoVO();
        BeanUtils.copyProperties(detectInfo, detectInfoVO);

        return detectInfoVO;

    }

}
