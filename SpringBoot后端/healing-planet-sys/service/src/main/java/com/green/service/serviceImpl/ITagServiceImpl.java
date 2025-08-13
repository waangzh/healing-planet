package com.green.service.serviceImpl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.green.entity.Tag;
import com.green.mapper.TagMapper;
import com.green.mapper.PostMapper;
import com.green.service.ITagService;
import com.green.vo.PostVO;
import com.green.vo.TagVO;
import com.green.service.ITopicTagService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Tag 实现类
 *
 * @author Knox 2020/11/7
 */
@Service
public class ITagServiceImpl extends ServiceImpl<TagMapper, Tag> implements ITagService {

    @Autowired
    private ITopicTagService ITopicTagService;


    @Autowired
    private PostMapper topicMapper;


    @Override
    public List<Tag> insertTags(List<String> tagNames) {
        List<Tag> tagList = new ArrayList<>();
        for (String tagName : tagNames) {
            Tag tag = this.baseMapper.selectOne(new LambdaQueryWrapper<Tag>().eq(Tag::getName, tagName));
            if (tag == null) {
                tag = Tag.builder().name(tagName).build();
                this.baseMapper.insert(tag);
            } else {
                tag.setCount(tag.getCount() + 1);
                this.baseMapper.updateById(tag);
            }
            tagList.add(tag);
        }
        return tagList;
    }

    @Override
    public Page<PostVO> selectTopicsByTagId(Page<PostVO> topicPage, String id) {

        // 获取关联的话题ID
        Set<String> ids = ITopicTagService.selectTopicIdsByTagId(id);


        return topicMapper.selectByTagId(topicPage, ids);
    }

    /**
     * 获取所有标签
     * @param category
     * @return
     */
    @Override
    public List<TagVO> listAll(Integer category) {
        LambdaQueryWrapper<Tag> queryWrapper = new LambdaQueryWrapper<Tag>().eq(Tag::getCategory,category);
        List<Tag> tags = this.baseMapper.selectList(queryWrapper);
        List<TagVO> tagVOList = new ArrayList<>();
        for (Tag tag : tags) {
            TagVO tagVO = new TagVO();
            BeanUtils.copyProperties(tag,tagVO);
            tagVOList.add(tagVO);
        }

        return tagVOList;
    }

    /**
     * 根据标签id获取文章id
     * @param tagId
     * @return
     */
    @Override
    public List<String> getPostIdsByTagId(List<String> tagIds) {
        return topicMapper.selectPostIdsByTagId(tagIds);
    }

}
