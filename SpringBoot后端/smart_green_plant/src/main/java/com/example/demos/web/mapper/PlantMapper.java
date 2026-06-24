package com.example.demos.web.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.demos.web.pojo.entity.Plant;
import org.apache.ibatis.annotations.Mapper;
import org.springframework.stereotype.Repository;

@Mapper
@Repository

public interface PlantMapper extends BaseMapper<Plant> {

}
