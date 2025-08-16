package com.green.controller.admin;


import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.green.common.api.Result;
import com.green.dto.PostDTO;
import com.green.entity.PostQuery;
import com.green.service.IPostService;
import com.green.vo.PostVO;
import com.vdurmont.emoji.EmojiParser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Date;
import java.util.List;
import java.util.Map;

import static com.green.security.jwt.JwtUtil.USER_NAME;

@RestController
@RequestMapping("/admin/post")
@Slf4j
public class AdminPostController {

    @Autowired
    private IPostService postService;

    /**
     * 获取文章列表
     * @param tab
     * @param pageNo
     * @param pageSize
     * @return
     */
    @PostMapping("/list")
    public Result<Page<PostVO>> list(@RequestBody PostQuery postQuery) {
        log.info("获取文章列表:{}",postQuery);
        Page<PostVO> list = postService.getPosts(postQuery);

        return Result.success(list);
    }

    /**
     * 更新文章
     * @param postDTO
     * @return
     */
    @PutMapping("/update")
    public Result<?> update(@RequestHeader(value = USER_NAME) String userName,@RequestBody PostDTO postDTO){
        log.info("管理员{}修改文章:{}",userName,postDTO);
        postDTO.setModifyTime(new Date());
        postDTO.setContent(EmojiParser.parseToAliases(postDTO.getContent()));
        postService.update(postDTO);
        return Result.success();
    }


    /**
     * 根据id删除文章
     * @param ids
     * @return
     */
    @DeleteMapping("/delete")
    public Result<?> delete(@RequestParam List<String> ids){
        log.info("根据id删除文章:{}",ids);
        postService.delete(ids);
        return Result.success();
    }

    /**
     * 根据id查询文章
     * @param id
     * @return
     */
    @GetMapping("")
    public Result<?> getById(@RequestParam String id){
        log.info("根据id查询文章:{}",id);
        Map<String, Object> map = postService.viewTopic(id);
        return Result.success(map);
    }


}
