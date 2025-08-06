package com.green.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.green.entity.PostLikes;
import com.green.entity.PostLikes;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Select;
import org.springframework.stereotype.Repository;

@Repository
public interface PostLikesMapper extends BaseMapper<PostLikes> {

    @Select("select * from post_likes where user_id=#{userId} and topic_id=#{topicId}")
    PostLikes select(String topicId, String userId);

    @Delete("delete from post_likes where topic_id=#{topicId} and user_id=#{userId}")
    void delete(String topicId, String userId);
}
