package com.green.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.green.entity.User;
import com.green.entity.UserQuery;
import com.green.vo.UserVO;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 用户
 *
 * @author Knox 2020/11/7
 */
@Repository
public interface UserMapper extends BaseMapper<User> {

    /**
     * 分页查询所有用户
     * @param userQuery
     * @return
     */
    List<UserVO> selectUserBaseList(@Param("query") UserQuery userQuery);
}
