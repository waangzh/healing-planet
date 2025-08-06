package com.green.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.green.dto.CommentDTO;
import com.green.entity.Comment;
import com.green.entity.User;
import com.green.vo.CommentVO;
import java.util.List;


public interface ICommentService extends IService<Comment> {
    /**
     * 根据文章标题i获取评论
     *
     * @param topicid
     * @return {@link Comment}
     */
    List<CommentVO> getCommentsByTopicID(String topicid);

    CommentVO create(CommentDTO dto, User principal);
}
