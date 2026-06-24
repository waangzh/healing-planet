package com.example.demos.web.service;


import com.example.demos.web.common.result.Result;
import com.example.demos.web.pojo.dto.UserDTO;
import com.example.demos.web.pojo.entity.User;
import com.example.demos.web.pojo.vo.AccountVO;

import java.util.List;

//public interface UserService extends IService<User> {
public interface UserService{

    /**
     * 注册
     * @param user
     * @return
     */
    Result<?> register(User user);
    /**
     * 登录
     * @param dto
     * @return
     */
    Result<?> login(UserDTO dto);

    /**
     * 新增用户
     * @param user
     * @return
     */
    Result<?> save(User user);

    /**
     * 修改密码
     * @param user
     * @return
     */
    Result<?> password(Integer id, String password);
    //仅更新密码
    //  /update/password
    Result<?> update(User user);

    /**
     * 批量删除用户
     * @param ids
     * @return
     */
    Result<?> deleteBatch(List<Integer> ids);
;
    ////分页查询用户
    //
    //Result<?> findPage(Integer pageNum, Integer pageSize, String search);
    ////多条件搜索用户
    //Result<?> findPage2(Integer pageNum, Integer pageSize, String search1,
    //                           String search2, String search3, String search4);

    /**
     * 查询所有用户
     * @return
     */
    Result<?> search();

    /**
     * 根据id查询用户
     * @param id
     * @return
     */
    Result<?> selectById(Integer id);

    /**
     * 根据key修改密码
     *
     * @param key
     * @param changePasswrod
     * @return
     */
    AccountVO changePassword(String key, String changePasswrod);
}
