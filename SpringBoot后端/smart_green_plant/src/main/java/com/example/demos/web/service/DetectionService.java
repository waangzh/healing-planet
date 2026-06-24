package com.example.demos.web.service;

import com.example.demos.web.pojo.dto.DetectInfoDTO;
import com.example.demos.web.pojo.vo.DetectInfoVO;
import org.springframework.web.multipart.MultipartFile;

public interface DetectionService {
    /**
     * 作物病理检测接口
     *
     * @param detectInfoDTO
     * @param image
     * @return
     */
    DetectInfoVO detectPlantDiseaseFromImage(DetectInfoDTO detectInfoDTO, MultipartFile image) throws Exception;
}