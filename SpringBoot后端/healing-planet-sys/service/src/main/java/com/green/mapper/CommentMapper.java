package com.green.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.green.entity.Comment;
import com.green.vo.CommentVO;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

import java.util.List;


@Repository
public interface CommentMapper extends BaseMapper<Comment> {

    /**
     * 根据标题获取评论
     *
     * @param topicid
     * @return
     */
    List<CommentVO> getCommentsByTopicID(@Param("topicid") String topicid);

    /**
     * 查询所有以及评论
     * @param topicId
     * @return
     */
    List<CommentVO> selectRootComments(String topicId);

    /**
     * 查询对应的子评论
     * @param id
     * @return
     */
    List<CommentVO> selectByRootId(String id);
}
