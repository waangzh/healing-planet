package com.green.service.serviceImpl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.green.common.exception.ApiException;
import com.green.dto.CreateTopicDTO;
import com.green.dto.PostDTO;
import com.green.mapper.TagMapper;
import com.green.mapper.PostMapper;
import com.green.mapper.UserMapper;
import com.green.mapper.UserPostViewMapper;
import com.green.entity.Post;
import com.green.entity.Tag;
import com.green.entity.TopicTag;
import com.green.entity.User;
import com.green.service.IPostService;
import com.green.vo.PostVO;
import com.green.vo.ProfileVO;
import com.green.service.ITagService;
import com.green.service.ITopicTagService;
import com.green.service.IUmsUserService;
import com.vdurmont.emoji.EmojiParser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.ObjectUtils;

import javax.annotation.Resource;
import java.util.*;
import java.util.stream.Collectors;


@Service
public class IPostServiceImpl extends ServiceImpl<PostMapper, Post> implements IPostService {
    @Resource
    private TagMapper tagMapper;
    @Resource
    private UserMapper userMapper;

    @Autowired
    @Lazy
    private ITagService tagService;

    @Autowired
    private IUmsUserService iUmsUserService;


    @Autowired
    private ITopicTagService topicTagService;
    @Autowired
    private UserPostViewMapper userPostViewMapper;

    /**
     * 查询话题
     * @param page
     * @param tab
     * @return
     */
    @Override
    public Page<PostVO> getList(Page<PostVO> page, String tab) {
        Page<PostVO> ipage = null;
        if(tab.equals("latest") || tab.equals("hot")) {
            // 查询热门/最新话题
            ipage = this.baseMapper.selectListAndPage(page, tab);

        }else{
            LambdaQueryWrapper<Tag> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(Tag::getName,tab);
            Tag one = tagMapper.selectOne(wrapper);
            if(one==null) {
                throw new ApiException("标签不存在，或已被管理员删除");
            }
            ipage = tagService.selectTopicsByTagId(
                    page,one.getId());

        }
        // 查询话题的标签
        setTopicTags(ipage);
        return ipage;
    }

    /**
     * 发布话题/文章
     * @param dto
     * @param user
     * @return
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Post create(CreateTopicDTO dto, User user) {
        //Post topic1 = this.baseMapper.selectOne(new LambdaQueryWrapper<Post>().eq(Post::getTitle, dto.getTitle()));
        ////Assert.isNull(topic1, "话题已存在，请修改");
        //if(topic1 != null){
        //    throw new ApiException("标题已存在，请修改");
        //}
        // 封装
        Post topic = Post.builder()
                .userId(user.getId())
                .title(dto.getTitle())
                .content(EmojiParser.parseToAliases(dto.getContent()))
                .coverImg(dto.getCoverImg())
                .createTime(new Date())
                .build();
        this.baseMapper.insert(topic);

        // 用户积分增加
        int newScore = user.getScore() + 1;
        userMapper.updateById(user.setScore(newScore));

        //// 标签(允许自定义标签)
        //if (!ObjectUtils.isEmpty(dto.getTags())) {
        //    // 保存标签
        //    List<Tag> tags = tagService.insertTags(dto.getTags());
        //    // 处理标签与话题的关联

        //}
        if(!ObjectUtils.isEmpty((dto.getTags()))){
            List<Tag> tags = new ArrayList<>();
            for(String tagId : dto.getTags()){
                Tag tag = tagMapper.selectById(tagId);
                tags.add(tag);
            }
            topicTagService.createTopicTag(topic.getId(), tags);
        }



        return topic;
    }

    @Override
    public Map<String, Object> viewTopic(String id) {
        Map<String, Object> map = new HashMap<>(16);
        Post topic = this.baseMapper.selectById(id);
        if(topic == null){
            throw new ApiException("当前话题不存在,或已被作者删除");
        }
        // 查询话题详情
        topic.setView(topic.getView() + 1);
        this.baseMapper.updateById(topic);
        // emoji转码
        topic.setContent(EmojiParser.parseToUnicode(topic.getContent()));
        map.put("topic", topic);
        // 标签
        QueryWrapper<TopicTag> wrapper = new QueryWrapper<>();
        wrapper.lambda().eq(TopicTag::getTopicId, topic.getId());
        Set<String> set = new HashSet<>();
        List<TopicTag> list = topicTagService.list(wrapper);
        if(list!=null && list.size()>0){
            for (TopicTag articleTag : list) {
                set.add(articleTag.getTagId());
            }
            List<Tag> tags = tagService.listByIds(set);
            map.put("tags", tags);
        }else{
            map.put("tags", null);
        }
        // 作者
        ProfileVO user = iUmsUserService.getUserProfile(topic.getUserId());
        map.put("user", user);



        return map;
    }

    @Override
    public List<Post> getRecommend(String id) {
        return this.baseMapper.selectRecommend(id);
    }
    @Override
    public Page<PostVO> searchByKey(String keyword, Page<PostVO> page) {
        // 查询话题
        Page<PostVO> iPage = this.baseMapper.searchByKey(page, keyword);
        // 查询话题的标签
        setTopicTags(iPage);
        return iPage;
    }

    /**
     * 更新文章
     * @param postDTO
     */
    @Override
    public void update(PostDTO postDTO) {
        Post post = this.baseMapper.selectById(postDTO.getId());
        // 更新
        post.setTitle(postDTO.getTitle());
        post.setContent(postDTO.getContent());
        post.setCoverImg(postDTO.getCoverImg());
        post.setModifyTime(new Date());
        this.baseMapper.updateById(post);
        // 文章更新前的标签
        List<TopicTag> list = topicTagService.selectByTopicId(postDTO.getId());
        List<String> tags = postDTO.getTags();
        for(TopicTag topicTag : list){
            // 更新后的标签列表中不含有的以前的标签
            if(!tags.contains(topicTag.getTagId())){
                Tag tag = tagMapper.selectById(topicTag.getTagId());
                tag.setCount(tag.getCount() - 1);
                topicTagService.removeById(topicTag.getId());
            } else { // 更新后的包含了之前已有的，则在列表中删除
                tags.remove(topicTag.getTagId());
            }
        }
        for(String tagId : tags){
            // 新增的标签
            Tag tag = tagMapper.selectById(tagId);
            tag.setCount(tag.getCount() + 1);
            tagMapper.updateById(tag);
            TopicTag topicTag = TopicTag.builder()
                    .tagId(tagId)
                    .topicId(postDTO.getId())
                    .build();
            topicTagService.save(topicTag);
        }

    }


    private void setTopicTags(Page<PostVO> iPage) {
        iPage.getRecords().forEach(topic -> {
            List<TopicTag> topicTags = topicTagService.selectByTopicId(topic.getId());
            if (!topicTags.isEmpty()) {
                List<String> tagIds = topicTags.stream().map(TopicTag::getTagId).collect(Collectors.toList());
                List<Tag> tags = tagMapper.selectBatchIds(tagIds);
                topic.setTags(tags);
            }
        });
    }
}
