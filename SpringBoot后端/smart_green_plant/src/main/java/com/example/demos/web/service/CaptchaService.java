package com.example.demos.web.service;


import com.example.demos.web.pojo.entity.Captcha;

public interface CaptchaService {

    /**
     * 校验验证码
     * @param imageKey
     * @param imageCode
     * @return boolean
     **/
    String checkImageCode(String imageKey, String imageCode);
    /**
     * 缓存验证码，有效期15分钟
     * @param key
     * @param code
     **/
    void saveImageCode(String key, String code);

    /**
     * 获取验证码拼图（生成的抠图和带抠图阴影的大图及抠图坐标）
     **/
    Object getCaptcha(Captcha captcha);
}

