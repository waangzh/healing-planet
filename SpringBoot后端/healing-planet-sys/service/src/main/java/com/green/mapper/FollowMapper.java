package com.green.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.green.entity.Follow;
import org.apache.ibatis.annotations.MapKey;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

@Repository
public interface FollowMapper extends BaseMapper<Follow> {
    /**
     * 批量查询粉丝数，返回 userId -> followerCount 映射
     */
    @MapKey("userId")
    List<Map<String, Object>> selectFollowerCount(@Param("userIds") List<String> userIds);

    /**
     * 批量查询关注数，返回 userId -> followingCount 映射
     */
    @MapKey("userId")
    List<Map<String, Object>> selectFollowingCount(@Param("userIds") List<String> userIds);
}
