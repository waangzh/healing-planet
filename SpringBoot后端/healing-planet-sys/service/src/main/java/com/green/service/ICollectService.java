package com.green.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.green.entity.Collect;
import com.green.dto.CollectDTO;
import com.green.vo.CollectVO;


import java.util.HashMap;

public interface ICollectService extends IService<Collect> {

    /**
     * 获取用户收藏列表
     * @param currentId
     * @return
     */
    Page<CollectVO> list(Page<CollectVO> page, String currentId);

    /**
     * （取消）收藏文章
     * @param collectDTO
     * @return
     */
    HashMap<String, Object> isCollected(CollectDTO collectDTO);

    /**
     * 验证是否收藏
     * @param userName
     * @param postId
     * @return
     */
    Boolean validate(String userName, String postId);
}
