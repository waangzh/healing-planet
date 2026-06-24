package com.example.demos.web.controller;

import cn.hutool.captcha.CaptchaUtil;
import com.example.demos.web.common.result.Result;
import com.example.demos.web.constant.MessageConstant;
import com.example.demos.web.pojo.entity.Captcha;
import com.example.demos.web.service.CaptchaService;
import com.example.demos.web.utils.AliOssUtil;
import com.example.demos.web.utils.BaiDuUtil;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 通用接口
 */
@RestController
@RequestMapping("/common")
@Slf4j
public class CommonController {

    @Autowired
    private AliOssUtil aliOssUtil;
    @Autowired
    private BaiDuUtil baiDuUtil;
    @Autowired
    private CaptchaService captchaService;
    // 用于存储每个会话的对话历史
    private final ConcurrentHashMap<String, JSONArray> conversationHistory = new ConcurrentHashMap<>();


    /**
     * 文件上传
     *
     * @param file
     * @return
     */
    @PostMapping("/upload")
    public Result<String> upload(MultipartFile file){
        log.info("文件上传：{}",file);
        try {
            //获取原始文件名
            String originalFilename = file.getOriginalFilename();
            //获取文件后缀 .png/.jpg
            String extension = null;
            if (originalFilename != null) {
                extension = originalFilename.substring(originalFilename.lastIndexOf("."));
            }
            //构造新文件名
            String objectName = UUID.randomUUID().toString()+extension;
            //文件的请求路径
            String fliePath = aliOssUtil.upload(file.getBytes(),objectName);
            return Result.success(fliePath);
        } catch (IOException e) {
            log.error("文件上传失败：{}", e.getMessage());
        }

        return Result.error(MessageConstant.UPLOAD_FAILED);
    }

    /**
     * 百度ai对话
     * @param userMessage
     * @param id
     * @return
     */
    @PostMapping("/chat")
    public Result<String> chatWithBaidu(String userMessage,@RequestParam String id) {
        log.info("用户输入:{},id:{}",userMessage,id);
        //String prompt = "假设你是一名绿植养护专家，你叫小绿助手。";
        //userMessage += prompt;
        try {
            // 获取当前会话的历史记录，如果没有则初始化一个空的对话历史
            JSONArray messages = conversationHistory.getOrDefault(id, new JSONArray());
            JSONObject userMessageJson = new JSONObject();
            userMessageJson.put("role", "user");
            userMessageJson.put("content", userMessage);
            messages.put(userMessageJson);

            // 调用接口
            String response = baiDuUtil.callBaidu(baiDuUtil.getAccessToken(), messages,1);
            log.info("返回请求:{}",response);

            // 解析响应并返回模型的回答
            JSONObject responseJson = new JSONObject(response);
            JSONObject assistantMessageJson = new JSONObject();
            if (responseJson.has("result")) {
                String modelResponse = responseJson.getString("result");

                assistantMessageJson.put("role", "assistant");
                assistantMessageJson.put("content", modelResponse);
                messages.put(assistantMessageJson);
                // 更新会话历史
                conversationHistory.put(id, messages);

                return Result.success(modelResponse); // 返回模型的回答
            } else {
                return Result.error(response);
            }
        } catch (Exception e) {
            e.printStackTrace();
            return Result.error(e.getMessage());
        }
    }

    /**
     * 生成验证码拼图
     * @param captcha
     * @return
     */
    @PostMapping("/getCaptcha")
    public Result<?> getCaptcha(@RequestBody Captcha captcha) {
        log.info("生成验证码拼图:{}",captcha);
        return Result.success(captchaService.getCaptcha(captcha));
    }

}
