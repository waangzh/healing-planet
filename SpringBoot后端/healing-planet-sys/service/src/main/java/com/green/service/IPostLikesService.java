package com.green.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.green.entity.PostLikes;
import com.green.vo.PostLikesVO;

public interface IPostLikesService extends IService<PostLikes> {
    /**
     * 文章是否点赞
     * @param topicId
     * @param userId
     * @return
     */
    PostLikesVO togglePostLike(String topicId, String userId);

    /**
     * 验证是否点赞
     * @param userName
     * @param postId
     * @return
     */
    Boolean validate(String userName, String postId);
}
