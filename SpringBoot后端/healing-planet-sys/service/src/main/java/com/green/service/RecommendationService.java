package com.green.service;

import com.green.entity.User;
import com.green.vo.RecommendPostVO;
import com.green.vo.UserBindDeviceVO;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.apache.ibatis.annotations.Param;

import java.io.IOException;
import java.util.List;
import java.util.Map;


public interface RecommendationService {

    /**
     * 通过KEY值，绑定社区用户
     *
     * @param deviceKey
     * @param communityUser
     * @return
     */
    UserBindDeviceVO userBindDevice(String deviceKey, User communityUser) throws IOException;

    /**
     * 获取当前社区用户已绑定的花盆账号与植物实例。
     */
    Map<String, Object> getRagContext(User communityUser) throws IOException;

    /**
     * 文章推荐接口
     * @param pageNo
     * @param pageSize
     * @param communityUser
     * @return
     */
    List<RecommendPostVO> getPostsRecommendations(Integer pageNo, Integer pageSize, User communityUser) throws JsonProcessingException;

    /**
     * 猜你喜欢页面，使用大模型推荐文章
     * @param communityUser
     */
    void LLMRecommendationPosts(@Param("communityUser") User communityUser) throws Exception;

    /**
     * 大模型推荐你可能喜欢的用户
     * @param userName
     */
    void LLMRecommendationUsers(User userName) throws Exception;

    /**
     * 获取推荐用户接口
     * @param pageNo
     * @param pageSize
     * @param communityUser
     * @return
     */
    List<User> getUsersRecommendations(Integer pageNo, Integer pageSize, User communityUser) throws JsonProcessingException;

    /**
     * 根据用户后台数据，智能撰写日志
     *
     * @param userName
     * @param message
     * @return
     */
    String writePost(User userName, String message) throws IOException;
}
