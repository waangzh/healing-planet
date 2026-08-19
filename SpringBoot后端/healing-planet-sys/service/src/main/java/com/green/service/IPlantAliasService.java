package com.green.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.green.dto.PlantAliasDTO;
import com.green.entity.PlantAlias;
import com.green.vo.PlantAliasVO;

import java.util.Collection;
import java.util.List;

public interface IPlantAliasService extends IService<PlantAlias> {

    List<PlantAliasVO> listByPlantId(String plantId);

    void add(String plantId, PlantAliasDTO dto);

    void update(String plantId, String aliasId, PlantAliasDTO dto);

    void delete(String plantId, String aliasId);

    void deleteByPlantIds(Collection<String> plantIds);
}
