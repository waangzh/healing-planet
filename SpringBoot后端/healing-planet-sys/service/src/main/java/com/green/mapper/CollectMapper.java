package com.green.mapper;


import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.green.entity.Collect;
import org.apache.ibatis.annotations.Select;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CollectMapper extends BaseMapper<Collect> {
    /**
     * 取消收藏
     * @param collect
     */
    void deleteCollect(Collect collect);

    /**
     * 获取用户收藏列表
     * @param currentId
     * @return
     */
    @Select("select topic_id from collect where user_id = #{currentId}")
    List<String> selectByUserId(String currentId);
}
