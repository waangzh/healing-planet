package com.green.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.green.entity.Tip;
import org.springframework.stereotype.Repository;

@Repository
public interface TipMapper extends BaseMapper<Tip> {
    Tip getRandomTip();
}
