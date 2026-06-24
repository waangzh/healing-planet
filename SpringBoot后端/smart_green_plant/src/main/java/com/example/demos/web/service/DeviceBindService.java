package com.example.demos.web.service;


import com.example.demos.web.pojo.dto.BindKeyToUserDTO;
import com.example.demos.web.pojo.vo.BackEndAccountVO;

public interface DeviceBindService {
    /**
     * 通过uuid绑定好用户之后，将生成的后台账号和密码返回给前端用户，登录绿植平台
     * @param bindKeyToUserDTO
     * @return
     */
    BackEndAccountVO userBindKEY(BindKeyToUserDTO bindKeyToUserDTO);
}
