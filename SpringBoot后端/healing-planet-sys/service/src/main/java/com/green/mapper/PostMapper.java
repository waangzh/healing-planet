package com.green.mapper;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.green.entity.Post;
import com.green.entity.PostQuery;
import com.green.vo.CollectVO;
import com.green.vo.PostVO;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Repository
public interface PostMapper extends BaseMapper<Post> {
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

    /**
     * 批量查询文章数，返回 userId -> postCount 映射
     */
    List<Map<String, Object>> selectPostCount(@Param("userIds") List<String> userIds);

    @Select("select topic_id from post_tag where tag_id in #{tagIds}")
    List<String> selectPostIdsByTagId(List<String> tagIds);


    /**
     * 自定义多表关联分页查询文章（带作者、标签）
     * @param page 分页对象
     * @param wrapper 过滤条件，只会用到wrapper里面拼接的Post字段条件
     * @return 分页后的PostVO列表
     */
    Page<PostVO> selectPostListWithUserAndTags(Page<PostVO> page, @Param("ew") Wrapper<Post> wrapper);
}
