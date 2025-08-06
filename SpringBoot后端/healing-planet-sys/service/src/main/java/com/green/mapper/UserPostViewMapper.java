package com.green.mapper;

import com.green.dto.PostSummaryDTO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface UserPostViewMapper {

    /**
     * 插入浏览记录
     * @param postId
     * @param userId
     */
    @Insert("insert into user_post_views (user_id, post_id, viewed_at) values (#{userId},#{postId},now()) ")
    void insertData(String postId, String userId);

    /**
     * 查询用户浏览历史表,获取对应的文章的id，以及对应的标题，标签，评论统计，收藏统计，浏览统计，点赞数，专栏名称
     *
     * @param id
     */
    @Select("SELECT p.id AS postId, " +
            "       p.title AS title, " +
            "       p.comments AS totalComments, " +
            "       p.collects AS totalCollects, " +
            "       p.view AS totalViews, " +
            "       p.likes AS totalLikes, " +
            "       COUNT(upv.post_id) AS userViewTotal, " +
            "       MAX(upv.viewed_at) AS viewTime " +
            "FROM green_community.post p " +
            "JOIN user_post_views upv ON p.id COLLATE utf8mb4_unicode_ci = upv.post_id COLLATE utf8mb4_unicode_ci " +
            "WHERE upv.user_id = #{id} " +
            "  AND DATEDIFF(CURDATE(), upv.viewed_at) < 7 " +
            "GROUP BY p.id " +
            "ORDER BY userViewTotal DESC, viewTime DESC " +
            "LIMIT 15")
    List<PostSummaryDTO> getViewsByUserId(String id);



    /**
     * 获取文章列表
     * @return
     */
    @Select("SELECT p.id AS postId, " +
            "       p.title AS title, " +
            "       p.comments AS totalComments, " +
            "       p.collects AS totalCollects, " +
            "       p.view AS totalViews, " +
            "       p.likes AS totalLikes , p.create_time as viewTime from green_community.post p where datediff(curdate() , create_time) < 60 limit 100")
    List<PostSummaryDTO> getPosts();
}
