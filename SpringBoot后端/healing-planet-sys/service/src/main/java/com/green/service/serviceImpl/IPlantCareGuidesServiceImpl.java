package com.green.service.serviceImpl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.green.entity.PlantCareGuides;
import com.green.mapper.PlantCareGuidesMapper;
import com.green.service.IPlantCareGuidesService;
import org.springframework.stereotype.Service;

@Service
public class IPlantCareGuidesServiceImpl extends ServiceImpl<PlantCareGuidesMapper, PlantCareGuides>
        implements IPlantCareGuidesService {
}
