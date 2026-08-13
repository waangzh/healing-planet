package com.example.demos.web.service;

import com.example.demos.web.pojo.vo.PlantStateVO;

import java.util.Optional;

public interface PlantStateService {
    Optional<PlantStateVO> getState(Integer plantInstanceId, Long userId);
}
