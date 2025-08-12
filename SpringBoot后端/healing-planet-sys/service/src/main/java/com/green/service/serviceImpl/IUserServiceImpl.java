package com.green.service.serviceImpl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.green.common.exception.ApiAsserts;
import com.green.common.exception.ApiException;
import com.green.entity.*;
import com.green.enumeration.ResultCodeEnum;
import com.green.security.jwt.JwtUtil;
import com.green.mapper.FollowMapper;
import com.green.mapper.RecommendationMapper;
import com.green.mapper.PostMapper;
import com.green.mapper.UserMapper;
import com.green.dto.LoginDTO;
import com.green.dto.RegisterDTO;
import com.green.dto.UserDTO;
import com.green.service.IUmsUserService;
import com.green.vo.LoginVO;
import com.green.vo.ProfileVO;
import com.green.vo.UserVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.ObjectUtils;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


@Slf4j
@Service
@Transactional(rollbackFor = Exception.class)
public class IUserServiceImpl extends ServiceImpl<UserMapper, User> implements IUmsUserService {

    @Autowired
    private PostMapper postMapper;
    @Autowired
    private FollowMapper followMapper;
    @Autowired
    private UserMapper userMapper;

    @Autowired
    private RecommendationMapper recommendationMapper;

    @Autowired
    private AuthenticationManager authenticationManager;



    /**
     * 注册
     * @param dto
     * @return
     */
    @Override
    public User executeRegister(RegisterDTO dto) {
        //查询是否有相同用户名的用户
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getUsername, dto.getName()).or().eq(User::getEmail, dto.getEmail());
        User user = baseMapper.selectOne(wrapper);
        if (!ObjectUtils.isEmpty(user)) {
            ApiAsserts.fail("账号或邮箱已存在！");
        }
        User addUser = User.builder()
                .username(dto.getName())
                .alias(dto.getName())
                //.password(MD5Utils.getPwd(dto.getPass()))
                .password(new BCryptPasswordEncoder().encode(dto.getPass()))
                .email(dto.getEmail())
                .avatar("https://smart-plant.oss-cn-hangzhou.aliyuncs.com/bdda5ec8-22d6-4e45-b61a-4f3f91a1ab60.png")
                .createTime(new Date())
                .status(true)
                .build();
        baseMapper.insert(addUser);

        UserPurchaseTags userPurchaseTags = UserPurchaseTags.builder()
                .communityUserId(addUser.getId())
                .recommendTags("未购买设备,绿植爱好者")
                .isPurchased(false)
                .build();

        recommendationMapper.addCommunityUser(userPurchaseTags);

        return addUser;
    }

    /**
     * 通过用户名获取用户
     * @param username
     * @return
     */
    @Override
    public User getUserByUsername(String username) {
        User user = baseMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUsername, username));
        if(user == null)
            throw new ApiException("用户不存在");
        return user;
    }

    /**
     * 登录
     * @param dto
     * @return
     */
    @Override
    public LoginVO executeLogin(LoginDTO dto) {
        try {
            UsernamePasswordAuthenticationToken authToken =
                    new UsernamePasswordAuthenticationToken(dto.getUsername(), dto.getPassword());

            Authentication authentication = authenticationManager.authenticate(authToken);

            org.springframework.security.core.userdetails.User principal =
                    (org.springframework.security.core.userdetails.User) authentication.getPrincipal();

            LoginVO vo = new LoginVO();
            vo.setToken(JwtUtil.generateToken(principal.getUsername()));
            return vo;
        } catch (UsernameNotFoundException e) {
            throw new ApiException("用户不存在");
        } catch (BadCredentialsException e) {
            throw new ApiException("用户名或密码错误");
        } catch (Exception e) {
            throw new ApiException("登录失败，请稍后重试");
        }
    }

    /**
     * 根据id获取用户信息
     * @param id 用户ID
     * @return
     */
    @Override
    public ProfileVO getUserProfile(String id) {
        ProfileVO profile = new ProfileVO();
        User user = baseMapper.selectById(id);
        if(user == null){
            throw new ApiException(ResultCodeEnum.USER_NOT_EXIST_ERROR.msg);
        }
        BeanUtils.copyProperties(user, profile);
        // 用户文章数
        int count = postMapper.selectCount(new LambdaQueryWrapper<Post>().eq(Post::getUserId, id));
        profile.setTopicCount(count);

        // 粉丝数
        int followers = followMapper.selectCount((new LambdaQueryWrapper<Follow>().eq(Follow::getParentId, id)));
        profile.setFollowerCount(followers);
        // 关注数
        Integer followCount = followMapper.selectCount(new LambdaQueryWrapper<Follow>().eq(Follow::getFollowerId, user.getId()));
        profile.setFollowCount(followCount);
        return profile;
    }

    /**
     * 获取用户详细信息
     * @param user
     * @return
     */
    @Override
    public UserVO getInfoDetail(User user) {
        UserVO userVO = new UserVO();
        BeanUtils.copyProperties(user,userVO);
        // 发布的文章数量
        userVO.setPostCount(postMapper.selectCount(new LambdaQueryWrapper<Post>().eq(Post::getUserId, user.getId())));
        // 关注者数量
        userVO.setFollowerCount(followMapper.selectCount(new LambdaQueryWrapper<Follow>().eq(Follow::getParentId, user.getId())));
        // 关注数量
        userVO.setFollowingCount(followMapper.selectCount(new LambdaQueryWrapper<Follow>().eq(Follow::getFollowerId, user.getId())));
        //设置是否购买设备
        List<Integer> list = recommendationMapper.existsBackEndUser(user.getId());
        if(!list.isEmpty())
            userVO.setIsPurchased(true);

        return userVO;
    }

    /**
     * 更新用户信息
     * @param userDTO
     * @param username
     */
    @Override
    public void update(String username, UserDTO userDTO) {
        User user = this.getUserByUsername(username);
        user.setBio(userDTO.getBio());
        user.setAlias(userDTO.getAlias());
        user.setAvatar(userDTO.getAvatar());
        user.setEmail(userDTO.getEmail());
        user.setMobile(userDTO.getMobile());
        user.setModifyTime(new Date());
        user.setMessage(userDTO.getMessage());
        this.updateById(user);
    }

    /**
     * 分页查询所有用户
     * @param userQuery
     * @return
     */
    @Override
    public Page<UserVO> getList(UserQuery userQuery) {
        // 查询所有符合基础条件的用户列表
        List<UserVO> userList = userMapper.selectUserBaseList(userQuery);

        // 没有符合基础条件的用户，返回空列表
        if (userList.isEmpty()) {
            Page<UserVO> page = new Page<>(userQuery.getPageNo(), userQuery.getPageSize(), 0);
            page.setRecords(Collections.emptyList());
            return page;
        }


        // 获取用户ID列表
        List<String> userIds = userList.stream()
                .map(UserVO::getId)
                .collect(Collectors.toList());

        // 查询文章数
        Map<String, Integer> postCountMap = postMapper.selectPostCount(userIds)
                .stream()
                .collect(Collectors.toMap(
                        m -> (String) m.get("userId"),
                        m -> ((Number) m.get("cnt")).intValue()
                ));

        // 查询粉丝数
        Map<String, Integer> followerCountMap = followMapper.selectFollowerCount(userIds)
                .stream()
                .collect(Collectors.toMap(
                        m -> (String) m.get("userId"),
                        m -> ((Number) m.get("cnt")).intValue()
                ));

        // 查询关注数
        Map<String, Integer> followingCountMap = followMapper.selectFollowingCount(userIds)
                .stream()
                .collect(Collectors.toMap(
                        m -> (String) m.get("userId"),
                        m -> ((Number) m.get("cnt")).intValue()
                ));

        // 组装统计字段
        userList.forEach(user -> {
            user.setPostCount(postCountMap.getOrDefault(user.getId(), 0));
            user.setFollowerCount(followerCountMap.getOrDefault(user.getId(), 0));
            user.setFollowingCount(followingCountMap.getOrDefault(user.getId(), 0));
        });

        // 根据 UserQuery 的过滤条件，在Java层过滤结果
        List<UserVO> filteredList = userList.stream()
                .filter(user -> userQuery.getPostCount() == null || user.getPostCount() >= userQuery.getPostCount())
                .filter(user -> userQuery.getFollowerCount() == null || user.getFollowerCount() >= userQuery.getFollowerCount())
                .filter(user -> userQuery.getFollowingCount() == null || user.getFollowingCount() >= userQuery.getFollowingCount())
                .collect(Collectors.toList());

        // 分页
        int pageNo = userQuery.getPageNo() <= 0 ? 1 : userQuery.getPageNo();
        int pageSize = userQuery.getPageSize() <= 0 ? 10 : userQuery.getPageSize();
        int total = filteredList.size();

        int fromIndex = (pageNo - 1) * pageSize;
        if (fromIndex >= total) {
            Page<UserVO> page = new Page<>(userQuery.getPageNo(), userQuery.getPageSize(), total);
            page.setRecords(Collections.emptyList());
            return page;
        }
        int toIndex = Math.min(fromIndex + pageSize, total);
        List<UserVO> pageList = filteredList.subList(fromIndex, toIndex);

        // 构造分页对象返回
        Page<UserVO> page = new Page<>(pageNo, pageSize);
        page.setTotal(total);
        page.setRecords(pageList);

        return page;
    }


}
