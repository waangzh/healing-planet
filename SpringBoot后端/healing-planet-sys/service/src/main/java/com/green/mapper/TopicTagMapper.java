package com.green.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.green.entity.TopicTag;
import com.green.entity.TopicTag;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Set;


@Repository
public interface TopicTagMapper extends BaseMapper<TopicTag> {
    /**
     * 根据标签获取话题ID集合
     *
     * @param id
     * @return
     */
    Set<String> getTopicIdsByTagId(@Param("id") String id);

    /**
     * 根据文章id获取标签id
     * @param ids
     * @return
     */
    List<String> getTagIdByPostId(List<String> ids);


    void deleteByPostIds(List<String> ids);
}
