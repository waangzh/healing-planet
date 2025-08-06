package com.green.controller;

import com.green.common.api.Result;
import com.green.dto.CommentDTO;
import com.green.entity.User;
import com.green.service.ICommentService;
import com.green.service.IUmsUserService;
import com.green.vo.CommentVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;



@RestController
@RequestMapping("/comment")
@Slf4j
public class CommentController extends BaseController {

    @Resource
    private ICommentService iCommentService;
    @Resource
    private IUmsUserService iUserService;

    /**
     * 获取评论
     * @param topicid
     * @return
     */
    @GetMapping("/get_comments")
    public Result<List<CommentVO>> getCommentsByTopicID(@RequestParam(value = "topicId", defaultValue = "1") String topicId) {
        List<CommentVO> lstBmsComment = iCommentService.getCommentsByTopicID(topicId);
        return Result.success(lstBmsComment);
    }

    /**
     * 添加评论
     * @param userName
     * @param dto
     * @return
     */
    @PostMapping("/add_comment")
    public Result<CommentVO> add_comment(@RequestBody CommentDTO dto) {
        log.info("添加评论:{}",dto);
        User user = iUserService.getUserByUsername(dto.getUserName());
        CommentVO vo = iCommentService.create(dto, user);
        return Result.success(vo);
    }
}
