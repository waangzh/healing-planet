package com.green.service.serviceImpl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.green.entity.TopicTag;
import com.green.mapper.TagMapper;
import com.green.mapper.TopicTagMapper;
import com.green.entity.Tag;
import com.green.entity.TopicTag;
import com.green.service.ITopicTagService;
import com.green.mapper.TagMapper;
import com.green.service.ITopicTagService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;


@Service
@Transactional(rollbackFor = Exception.class)
public class ITopicTagServiceImpl extends ServiceImpl<TopicTagMapper, TopicTag> implements ITopicTagService {

    private final TagMapper tagMapper;

    public ITopicTagServiceImpl(@Qualifier("tagMapper") TagMapper tagMapper) {
        this.tagMapper = tagMapper;
    }

    @Override
    public List<TopicTag> selectByTopicId(String topicId) {
        QueryWrapper<TopicTag> wrapper = new QueryWrapper<>();
        wrapper.lambda().eq(TopicTag::getTopicId, topicId);
        return this.baseMapper.selectList(wrapper);
    }
    @Override
    public void createTopicTag(String id, List<Tag> tags) {
        // 先删除topicId对应的所有记录
        this.baseMapper.delete(new LambdaQueryWrapper<TopicTag>().eq(TopicTag::getTopicId, id));

        // 循环保存对应关联
        tags.forEach(tag -> {
            TopicTag topicTag = new TopicTag();
            topicTag.setTopicId(id);
            topicTag.setTagId(tag.getId());
            tag.setCount(tag.getCount() + 1);
            tagMapper.updateById(tag);
            this.baseMapper.insert(topicTag);
        });

    }
    @Override
    public Set<String> selectTopicIdsByTagId(String id) {
        return this.baseMapper.getTopicIdsByTagId(id);
    }

}
