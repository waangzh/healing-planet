package com.green.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.green.common.api.Result;
import com.green.mapper.UserPostViewMapper;
import com.green.dto.CreateTopicDTO;
import com.green.dto.PostDTO;
import com.green.entity.Post;
import com.green.entity.User;
import com.green.vo.PostVO;
import com.green.service.IPostService;
import com.green.service.IUmsUserService;
import com.vdurmont.emoji.EmojiParser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.validation.Valid;
import java.util.*;

import static com.green.security.jwt.JwtUtil.USER_NAME;


@RestController
@RequestMapping("/post")
@Slf4j
public class PostController extends BaseController {

    @Resource
    private IPostService iPostService;
    @Resource
    private IUmsUserService iUserService;
    @Autowired
    private RedisTemplate redisTemplate;
    @Autowired
    private UserPostViewMapper userPostViewMapper;

    /**
     * 获取文章列表
     * @param tab
     * @param pageNo
     * @param pageSize
     * @return
     */
    @GetMapping("/list")
    public Result<Page<PostVO>> list(@RequestParam(value = "tab", defaultValue = "latest") String tab,
                                     @RequestParam(value = "pageNo", defaultValue = "1")  Integer pageNo,
                                     @RequestParam(value = "size", defaultValue = "10") Integer pageSize) {
        log.info("获取文章列表:{}",tab);
        Page<PostVO> list = iPostService.getList(new Page<>(pageNo, pageSize), tab);

        return Result.success(list);
    }

    /**
     * 发布推文
     * @param userName
     * @param dto
     * @return
     */
    @PostMapping("/create")
    @Cacheable(cacheNames = "userInfo",key = "#userName")
    public Result<Post> create(@RequestHeader(value = USER_NAME) String userName
            , @RequestBody CreateTopicDTO createTopicDTO) {
        log.info("发布推文,作者:{},内容:{}",userName,createTopicDTO);
        User user = iUserService.getUserByUsername(userName);
        Post topic = iPostService.create(createTopicDTO, user);
        return Result.success(topic);
    }

    /**
     * 根据id获取指定文章
     * @param id
     * @return
     */
    @GetMapping()
    public Result<Map<String, Object>> view(@RequestParam("id") String id) {
        log.info("根据id获取指定文章详细信息:{}",id);
        Map<String, Object> map = iPostService.viewTopic(id);
        return Result.success(map);
    }

    @GetMapping("/postLog")
    public Result<Map<String, Object>> viewLog(@RequestHeader(value = USER_NAME) String userName,@RequestParam("id") String id) {
        User communityUser = iUserService.getUserByUsername(userName);
        userPostViewMapper.insertData(id,communityUser.getId());
        return Result.success();
    }


    /**
     * 获取详情页推荐
     * @param id
     * @return
     */
    @GetMapping("/recommend")
    public Result<List<Post>> getRecommend(@RequestParam("topicId") String id) {
        List<Post> topics = iPostService.getRecommend(id);
        return Result.success(topics);
    }

    /**
     * 更新文章
     * @param userName
     * @param post
     * @return
     */
    @PostMapping("/update")
    public Result<Post> update(@RequestHeader(value = USER_NAME) String userName, @Valid @RequestBody PostDTO postDTO) {
        log.info("用户{}更新文章:{}",userName,postDTO);
        User umsUser = iUserService.getUserByUsername(userName);
        postDTO.setModifyTime(new Date());
        postDTO.setContent(EmojiParser.parseToAliases(postDTO.getContent()));
        iPostService.update(postDTO);

        return Result.success();
    }

    /**
     * 删除文章
     * @param userName
     * @param id
     * @return
     */
    @DeleteMapping("/delete/{id}")
    @Cacheable(cacheNames = "userInfo",key = "#userName")
    public Result<String> delete(@RequestHeader(value = USER_NAME) String userName, @PathVariable("id") String id) {
        log.info("删除文章：{}，清除文章列表缓存.",id);
        User umsUser = iUserService.getUserByUsername(userName);
        Post byId = iPostService.getById(id);
        Assert.notNull(byId, "来晚一步，话题已不存在");
        Assert.isTrue(byId.getUserId().equals(umsUser.getId()), "你为什么可以删除别人的话题？？？");
        List<String> ids = new ArrayList<>();
        ids.add(id);
        iPostService.delete(ids);
        // 清除缓存
        cleanCache("post_*");
        return Result.success(null,"删除成功");
    }


    /**
     * 清理缓存数据
     * @param pattern
     */
    private void cleanCache(String pattern){
        Set<String> keys = redisTemplate.keys(pattern);
        redisTemplate.delete(keys);
    }
}
