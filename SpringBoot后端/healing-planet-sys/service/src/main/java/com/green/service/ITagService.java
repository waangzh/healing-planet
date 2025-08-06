package com.green.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.green.entity.Tag;
import com.green.vo.PostVO;
import com.green.vo.TagVO;


import java.util.List;


public interface ITagService extends IService<Tag> {
    /**
     * 插入标签
     *
     * @param tags
     * @return
     */
    List<Tag> insertTags(List<String> tags);
    /**
     * 获取标签关联话题
     *
     * @param topicPage
     * @param id
     * @return
     */
    Page<PostVO> selectTopicsByTagId(Page<PostVO> topicPage, String id);

    /**
     * 获取所有标签
     * @param category
     * @return
     */
    List<TagVO> listAll(Integer category);

}
