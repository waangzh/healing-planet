package com.green.controller.admin;


import com.green.common.api.Result;
import com.green.dto.TagDTO;
import com.green.entity.Tag;
import com.green.service.ITagService;
import com.green.vo.TagVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin/tag")
@Slf4j
public class AdminTagController {

    @Autowired
    private ITagService tagService;

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
     * 新增标签
     * @param tagDTO
     * @return
     */
    @PostMapping("/add")
    public Result<?> addTag(@RequestBody TagDTO tagDTO){
        log.info("新增标签:{}",tagDTO);
        Tag tag = new Tag();
        BeanUtils.copyProperties(tagDTO,tag);
        tagService.save(tag);
        return Result.success();
    }

    /**
     * 批量删除标签
     * @param ids
     * @return
     */
    @DeleteMapping("/delete")
    public Result<?> deleteTag(@RequestParam List<String> ids){
        log.info("删除标签:{}",ids);
        tagService.removeByIds(ids);
        return Result.success();
    }

    /**
     * 更新标签
     * @param tagDTO
     * @return
     */
    @PutMapping("/update")
    public Result<?> updateTag(@RequestBody TagDTO tagDTO){
        log.info("更新标签:{}",tagDTO);
        Tag tag = new Tag();
        BeanUtils.copyProperties(tagDTO,tag);
        tagService.updateById(tag);
        return Result.success();
    }
}
