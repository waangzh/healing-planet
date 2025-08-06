package com.green.mapper;


import com.green.entity.UserPurchaseTags;
import com.green.entity.User;
import com.green.vo.RecommendPostVO;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface RecommendationMapper {

    /**
     * 在创建用户时，将其信息加入推荐中间表
     * @param userPurchaseTags
     */
    @Insert("insert ignore into user_purchase_tags (device_id,is_purchased,recommend_tags,community_user_id,back_end_user_id,device_key,account) " +
            "values (#{deviceId},#{isPurchased},#{recommendTags},#{communityUserId},#{backEndUserId},#{deviceKey},#{account})")
    void addCommunityUser(UserPurchaseTags userPurchaseTags);

    /**
     * 根据deviceKey绑定绿植用户
     * @param userPurchaseTags
     */
    @Update("UPDATE user_purchase_tags SET " +
            "device_id = #{deviceId}, " +
            "is_purchased = #{isPurchased}, " +
            "recommend_tags = #{recommendTags}, " +
            "back_end_user_id = #{backEndUserId} ," +
            "device_key = #{deviceKey},account = #{account} " +
            "where community_user_id = #{communityUserId}" )

    void updateUser(UserPurchaseTags userPurchaseTags);


    @Select("SELECT back_end_user_id \n" +
            "FROM user_purchase_tags \n" +
            "WHERE community_user_id = #{id} \n" +
            "  AND back_end_user_id IS NOT NULL;")
    List<Integer> existsBackEndUser(String id);




    /**
     * 分页获取推荐文章
     * @param list
     * @return
     */
    List<RecommendPostVO> getPostsByList(@Param("ids") List<String> list);


    /**
     * 获取用户关注的人的信息
     * @param id
     * @return
     */
    @Select("select * from green_community.user where id in (select follow.parent_id  from green_community.follow where follower_id = #{id})")
    List<User> getUsersYouFollowed(String id);


    @Select("select * from green_community.user order by RAND() limit 50")
    List<User> getUsers();



    /**
     * 插入猜你喜欢推荐的文章列表
     *
     * @param result
     * @param userId
     */
    void insretRecommendationPosts(@Param("result") String result, @Param("userId") String userId);

    /**
     * 删除超过两天的推荐文章
     * @param id
     */
    @Delete("delete from user_recommendations where datediff(now(),create_time) > 2 and user_id = #{id}")
    void deleteRecommendation(String id);

    /**
     * 插入推荐用户id
     * @param string
     * @param id
     */
    void insretRecommendationUsers(String string, String id);

    /**
     * 从数据库中查找已经有的推荐用户的信息
     * @param id
     * @return
     */
    @Select("select recommend_posts from user_recommendations where user_id =#{id}")
    String getRecommendationUsers(String id);

    /**
     * 分页获取推荐用户
     * @param list
     * @return
     */
    List<User> getUsersByList(@Param("ids") List<String> list);


    @Select("select exists(select * from user_purchase_tags where device_key =#{deviceKey} )")
    Boolean existsKey(String deviceKey);
}
