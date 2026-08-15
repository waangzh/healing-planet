package com.example.demos.web.controller.shared;


import com.example.demos.web.common.result.Result;
import com.example.demos.web.service.DataSharedService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;


/**
 * 信息共享接口
 */
@RestController
@Slf4j
@RequestMapping("/shared/data")
public class DataSharedController {

    @Autowired
    DataSharedService dataSharedService;

    @PostMapping("/plants")
    public Result<List<String>> PlantDataShared(@RequestParam(value = "userId",required = true) String communityUserId){
        log.info("社区用户id：{}",communityUserId);
        return Result.success(dataSharedService.getPlantNames(communityUserId));
    }

    @PostMapping("/rag-context")
    public Result<Map<String, Object>> ragContext(
            @RequestParam(value = "communityUserId") String communityUserId) {
        return Result.success(dataSharedService.getRagContext(communityUserId));
    }


    @PostMapping("/post")
    public Result<String> writePostByData(String communityUserId,String msg) throws Exception {
        log.info("社区用户传来的智能撰写文章日志的功能:{},传递的写作要求{}",communityUserId, msg);
        String result = dataSharedService.writePost(communityUserId, msg);

        return Result.success(result);
    }


}
