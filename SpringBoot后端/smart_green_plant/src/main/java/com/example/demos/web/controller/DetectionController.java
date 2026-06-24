package com.example.demos.web.controller;

import com.example.demos.web.common.result.Result;
import com.example.demos.web.pojo.dto.DetectInfoDTO;
import com.example.demos.web.pojo.vo.DetectInfoVO;
import com.example.demos.web.service.DetectionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;


@Slf4j
@RestController
@RequestMapping("/model")
public class DetectionController {

    @Autowired
    DetectionService detectionService;

    /**
     * 病理检测接口，对接自训练检测模型
     *
     * @param image
     * @return
     */
    @PostMapping("/detection")
    public Result<DetectInfoVO> detectPlantDiseaseFromImage(@RequestPart("detectInfoDTO") String detectInfoJson,
                                                            @RequestPart("image") MultipartFile image) throws Exception {

        // 将 JSON 字符串转换为 DetectInfoDTO 对象
        ObjectMapper objectMapper = new ObjectMapper();
        DetectInfoDTO detectInfoDTO = objectMapper.readValue(detectInfoJson, DetectInfoDTO.class);

        log.info("检测设备信息：{}，检测图片信息：{}", detectInfoDTO, image);

        // 调用服务处理图像和检测信息
        DetectInfoVO detectInfoVO = detectionService.detectPlantDiseaseFromImage(detectInfoDTO, image);

        return Result.success(detectInfoVO);

    }

}
