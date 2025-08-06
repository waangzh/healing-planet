package com.green.controller;

import com.green.common.api.Result;
import com.green.entity.Tip;
import com.green.service.ITipService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/tip")
public class TipController extends BaseController {
    @Resource
    private ITipService TipService;
    @Autowired
    private RedisTemplate redisTemplate;
    /**
     * 获取每日赠言
     * @return
     */
    @GetMapping("/today")
    public Result<Tip> getRandomTip() {
        String key = "tips";
        Tip tips = (Tip)redisTemplate.opsForValue().get(key);
        if (tips == null) {
            tips = TipService.getRandomTip();
            // 获取当前时间
            LocalDateTime now = LocalDateTime.now();
            // 获取次日凌晨的时间
            LocalDateTime tomorrowMidnight = now.toLocalDate().plusDays(1).atTime(LocalTime.MIDNIGHT);
            // 计算当前时间到次日凌晨的时间间隔 / 秒
            long secondsUntilMidnight = ChronoUnit.SECONDS.between(now, tomorrowMidnight);
            redisTemplate.opsForValue().set(key, tips, secondsUntilMidnight, TimeUnit.SECONDS); // 次日0点过期
        }
        return Result.success(tips);
    }
}
