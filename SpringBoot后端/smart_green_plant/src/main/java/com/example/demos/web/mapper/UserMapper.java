package com.example.demos.web.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.demos.web.pojo.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.springframework.stereotype.Repository;

import java.util.List;
//BaseMapper<T>接口默认提供了基本的CRUD

//创建一个Mapper接口并继承BaseMapper<T>时,不需要显式地添加@Mapper注解
@Mapper
@Repository
public interface UserMapper extends BaseMapper<User> {
    @Select("select * from user")
    List<User> list();

    /**
     * 根据key值判断是否存在用户
     * @param key
     * @return
     */
    @Select("select back_end_user_id from binding_records where device_key=#{key}")
    Integer existsUser(String key);

    /**
     * 获取账号
     * @param id
     * @return
     */
    @Select("select username from user where id = #{id}")
    String getAccount(Integer id);
}
