package com.example.demos.web.service.impl;


import com.aliyun.credentials.utils.StringUtils;
import com.example.demos.web.exception.InvalidKeyException;
import com.example.demos.web.mapper.DeviceBindMapper;
import com.example.demos.web.mapper.PlantInstanceMapper;
import com.example.demos.web.mapper.UserMapper;
import com.example.demos.web.pojo.dto.BindKeyToUserDTO;
import com.example.demos.web.pojo.entity.Device;
import com.example.demos.web.pojo.entity.PlantInstance;
import com.example.demos.web.pojo.entity.User;
import com.example.demos.web.pojo.vo.BackEndAccountVO;
import com.example.demos.web.service.DeviceBindService;
import com.example.demos.web.service.DeviceService;
import com.example.demos.web.service.UserService;
import com.example.demos.web.utils.AccountGenerator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;


@Service
public class DeviceBindServiceImpl implements DeviceBindService {

    @Autowired
    DeviceBindMapper deviceBindMapper;

    @Autowired
    UserService userService;

    @Autowired
    PlantInstanceMapper plantInstanceMapper;

    @Qualifier("userMapper")
    @Autowired
    private UserMapper userMapper;

    /**
     * 通过uuid查询设备，如果有则绑定成功，生成账号和密码
     * 如果没有则返回错误
     * @param bindKeyToUserDTO
     * @return
     */
    @Override
    @Transactional
    public BackEndAccountVO userBindKEY(BindKeyToUserDTO bindKeyToUserDTO) {
        // 参数校验
        if (bindKeyToUserDTO == null) {
            throw new IllegalArgumentException("绑定信息不能为空");
        }

        // 获取uuid并校验
        String uuid = bindKeyToUserDTO.getDeviceKey();
        if (StringUtils.isEmpty(uuid)) {
            throw new InvalidKeyException("设备KEY不能为空");
        }

        // 通过uuid查询设备
        Integer deviceId = deviceBindMapper.findDeviceByKEY(uuid);
        if (deviceId == null) {
            throw new InvalidKeyException("您输入的KEY值有误，请重新输入");
        }

        // 获取社区用户ID并校验
        String communityUserId = bindKeyToUserDTO.getCommunityUserId();
        if (StringUtils.isEmpty(communityUserId)) {
            throw new IllegalArgumentException("社区用户ID不能为空");
        }

        // 查询是否已有后台账号
        List<Integer> listbackEndUserId = deviceBindMapper.existsCommunityById(communityUserId);
        Integer backEndUserId = Optional.ofNullable(listbackEndUserId)
                .filter(list -> !list.isEmpty())
                .map(list -> list.get(0))
                .orElse(null);

        String account = "";
        String password = "";
        User backEndUser = null;

        // 处理已有账号的情况
        if (backEndUserId != null) {
            backEndUser = userMapper.selectById(backEndUserId);
            if (backEndUser == null) {
                throw new IllegalStateException("查询到的用户ID对应的用户不存在");
            }
            account = Optional.ofNullable(backEndUser.getUsername()).orElse("");
            password = "您已获取过密码，如忘记密码请修改";
        }
        // 处理需要创建新账号的情况
        else {
            // 生成账号和密码
            account = AccountGenerator.generateAccount(8);
            password = AccountGenerator.generatePassword(8);

            // 创建用户
            User user = User.builder()
                    .username(account)
                    .password(password)
                    .build();

            // 注册用户
            try {
                userService.register(user);
                backEndUserId = Optional.ofNullable(user.getId())
                        .orElseThrow(() -> new IllegalStateException("用户注册失败，未能获取用户ID"));
            } catch (Exception e) {
                throw new IllegalStateException("用户注册失败: " + e.getMessage(), e);
            }
        }

        // 绑定设备
        try {
            deviceBindMapper.bindId(deviceId, backEndUserId, communityUserId);

            PlantInstance plantInstance = PlantInstance.builder()
                    .deviceId(deviceId)
                    .userId(backEndUserId)
                    .build();

            plantInstanceMapper.updateData(plantInstance);
        } catch (Exception e) {
            throw new RuntimeException("设备绑定失败: " + e.getMessage(), e);
        }

        // 返回结果
        return BackEndAccountVO.builder()
                .account(account)
                .password(password)
                .deviceId(deviceId)
                .backEndUserId(backEndUserId)
                .build();
    }
}
