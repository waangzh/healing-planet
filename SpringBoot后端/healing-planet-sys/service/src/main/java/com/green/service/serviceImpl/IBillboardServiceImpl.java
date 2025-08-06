package com.green.service.serviceImpl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.green.entity.Billboard;
import com.green.mapper.BillboardMapper;
import com.green.service.IBillboardService;
import org.springframework.stereotype.Service;

@Service
public class IBillboardServiceImpl extends ServiceImpl<BillboardMapper, Billboard> implements IBillboardService {

}
