package com.green.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.green.entity.Tip;

public interface ITipService extends IService<Tip> {
    Tip getRandomTip();
}
