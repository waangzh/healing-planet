package com.green.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.green.common.api.Result;
import com.green.common.exception.ApiException;
import com.green.entity.Post;
import com.green.entity.Tag;
import com.green.service.ITagService;
import com.green.vo.PostVO;
import com.green.vo.TagVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/tag")
@Slf4j
public class TagController extends BaseController {

    @Resource
    private ITagService tagService;

    /**
     * 获取标签关联文章
     * @param tagName
     * @param page
     * @param size
     * @return
     */
    @GetMapping("/relatedPost")
    public Result<Map<String, Object>> getTopicsByTag(
            @RequestParam("name") String tagName,
            @RequestParam(value = "page", defaultValue = "1") Integer page,
            @RequestParam(value = "size", defaultValue = "10") Integer size) {

        Map<String, Object> map = new HashMap<>(16);

        LambdaQueryWrapper<Tag> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Tag::getName, tagName);
        Tag one = tagService.getOne(wrapper);
        if(one==null){
            throw new ApiException("标签不存在，或已被管理员删除");
        }
        Page<PostVO> topics = tagService.selectTopicsByTagId(new Page<>(page, size), one.getId());
        // 其他热门标签
        Page<Tag> hotTags = tagService.page(new Page<>(1, 10),
                new LambdaQueryWrapper<Tag>()
                        .notIn(Tag::getName, tagName)
                        .orderByDesc(Tag::getCount));

        map.put("topics", topics);
        map.put("hotTags", hotTags);

        return Result.success(map);
    }

    /**
     * 获取标签列表(1-文章\0-绿植)
     * @return
     */
    @GetMapping("/all")
    public Result<List<TagVO>> getTagList(@RequestParam Integer category){
        log.info("获取标签列表:{}",category == 1 ?"文章分类":"绿植分类");
        List<TagVO> tags = tagService.listAll(category);
        return Result.success(tags);
    }

    /**
     * 根据关键词搜索标签
     * @param keyword
     * @return
     */
    @GetMapping("")
    public Result<List<TagVO>> searchTagList(@RequestParam String keyword){
        log.info("搜索标签:{}",keyword);
        LambdaQueryWrapper<Tag> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(Tag::getName, keyword);
        wrapper.eq(Tag::getCategory,1);
        List<Tag> list = tagService.list(wrapper);
        List<TagVO> voList = new ArrayList<>();
        for(Tag tag : list){
            TagVO vo = new TagVO();
            vo.setId(tag.getId());
            vo.setName(tag.getName());
            voList.add(vo);
        }
        return Result.success(voList);
    }
}
