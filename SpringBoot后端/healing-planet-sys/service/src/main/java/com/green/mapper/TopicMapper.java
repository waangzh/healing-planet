package com.green.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.green.entity.Post;
import com.green.vo.CollectVO;
import com.green.vo.PostVO;

import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Set;

@Repository
public interface TopicMapper extends BaseMapper<Post> {
    /**
     * 分页查询首页话题列表
     *
     * @param page
     * @param tab
     * @return
     */
    Page<PostVO> selectListAndPage(@Param("page") Page<PostVO> page, @Param("tab") String tab);

    /**
     * 获取详情页推荐
     *
     * @param id
     * @return
     */
    List<Post> selectRecommend(@Param("id") String id);
    /**
     * 全文检索
     *
     * @param page
     * @param keyword
     * @return
     */
    Page<PostVO> searchByKey(@Param("page") Page<PostVO> page, @Param("keyword") String keyword);

    /**
     * 根据标签id获取关联文章
     * @param topicPage
     * @param ids
     * @return
     */
    Page<PostVO> selectByTagId(Page<PostVO> topicPage, Set<String> ids);

    /**
     * 根据收藏id获取文章
     * @param page
     * @param collectList
     * @return
     */
    Page<CollectVO> selectByCollectId(Page<CollectVO> page, List<String> ids);
}
