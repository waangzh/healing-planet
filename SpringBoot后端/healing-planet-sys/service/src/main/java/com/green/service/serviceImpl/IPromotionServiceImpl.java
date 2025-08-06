package com.green.service.serviceImpl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.green.entity.Promotion;
import com.green.mapper.PromotionMapper;
import com.green.service.IPromotionService;
import org.springframework.stereotype.Service;


@Service
public class IPromotionServiceImpl extends ServiceImpl<PromotionMapper, Promotion> implements IPromotionService {

}
