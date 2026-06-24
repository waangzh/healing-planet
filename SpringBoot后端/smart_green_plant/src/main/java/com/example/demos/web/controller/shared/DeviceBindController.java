package com.example.demos.web.controller.shared;

/*
* 先写简单一点
* 就是先给每个设备和用户生成一个唯一的密钥对，然后用户在购置物品之后可以获得该密钥对
*
* 就先是什么，生成一个密钥对，用户在博客端使用了密钥对，就自动将博客账号绑定到后台
*
* 获取密钥对的作用其实就是给博客端的用户打标签，然后根据标签做一个权重，哪些数据重点给用户推荐，哪些非重点
*
* 在博客界面可以拿这个密钥对，去绑定设备和用户
* */
import com.example.demos.web.common.result.Result;
import com.example.demos.web.pojo.dto.BindKeyToUserDTO;
import com.example.demos.web.pojo.vo.BackEndAccountVO;
import com.example.demos.web.service.DeviceBindService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 设备绑定接口
 */
@RestController
@RequestMapping("/shared/deviceBind")
@Slf4j
public class DeviceBindController {

    @Autowired
    DeviceBindService deviceBindService;

    //做推荐就最简单的做，主要就是推荐兴趣爱好相关的用户
    //TODO 后面上秘钥对，现在先简单过渡一下

    /**
     * 通过设备唯一的uuid号，提供新的用户名和密码
     * @param bindKeyToUserDTO
     * @return
     */
    @PostMapping("/getKey")
    public Result<BackEndAccountVO> bindKeyToUsers(BindKeyToUserDTO bindKeyToUserDTO){
        log.info("前端传递过来的用户数据:{}",bindKeyToUserDTO);
        BackEndAccountVO backEndAccountVO = deviceBindService.userBindKEY(bindKeyToUserDTO);
        log.info("回复的结果是：{}",backEndAccountVO);
        return Result.success(backEndAccountVO);
    }

}
